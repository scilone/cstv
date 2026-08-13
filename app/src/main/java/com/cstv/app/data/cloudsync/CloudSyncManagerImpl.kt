package com.cstv.app.data.cloudsync

import android.content.Context
import com.cstv.app.data.cloudsync.merge.SnapshotMerger
import com.cstv.app.data.local.dao.ProfileDao
import com.cstv.app.data.local.dao.ProfileSyncStateDao
import com.cstv.app.data.local.entity.ProfileSyncStateEntity
import com.cstv.app.data.local.storage.CstvSessionManager
import com.cstv.app.data.remote.CstvEtag
import com.cstv.app.data.remote.CstvErrorMapper
import com.cstv.app.data.remote.api.CstvObjectsApiService
import com.cstv.app.data.worker.CloudSyncWorker
import com.cstv.app.domain.network.NetworkMonitor
import com.cstv.app.domain.sync.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class CloudSyncManagerImpl @Inject constructor(
    private val profiles: ProfileDao,
    private val states: ProfileSyncStateDao,
    private val objects: CstvObjectsApiService,
    private val serializer: SnapshotSerializer,
    private val codec: SnapshotCodec,
    private val merger: SnapshotMerger,
    private val errors: CstvErrorMapper,
    private val network: NetworkMonitor,
    private val sessions: CstvSessionManager,
    @ApplicationContext private val appContext: Context,
    @javax.inject.Named("applicationScope") private val scope: kotlinx.coroutines.CoroutineScope
) : CloudSyncManager {
    private val mutableStatus = MutableStateFlow<CloudSyncStatus>(CloudSyncStatus.Idle)
    override val status: StateFlow<CloudSyncStatus> = mutableStatus.asStateFlow()
    private val locks = mutableMapOf<String, Mutex>()
    private val delayedSyncs = mutableMapOf<String, Job>()

    override suspend fun markDirty(profileId: Int, namespace: SyncNamespace) {
        val previous = states.get(profileId, namespace.wireName)
        states.upsert((previous ?: ProfileSyncStateEntity(profileId, namespace.wireName)).copy(pending = true, failureCode = null))
        CloudSyncWorker.enqueue(appContext)
        mutableStatus.value = CloudSyncStatus.Pending
        val key = key(profileId, namespace)
        synchronized(delayedSyncs) { delayedSyncs.remove(key)?.cancel() }
        val job = scope.launchCatching {
            delay(DEBOUNCE_MS)
            synchronizeNamespace(profileId, namespace)
        }
        synchronized(delayedSyncs) { delayedSyncs[key] = job }
    }

    override suspend fun synchronizeProfile(profileId: Int) {
        SyncNamespace.entries.forEach { synchronizeNamespace(profileId, it) }
    }

    private suspend fun synchronizeNamespace(profileId: Int, namespace: SyncNamespace) {
        if (!network.isCurrentlyOnline()) return
        val profile = profiles.getById(profileId) ?: return
        val remoteId = profile.remoteId ?: return
        val accountIdAtStart = sessions.get()?.accountId ?: return
        mutex(profileId, namespace).withLock {
            repeat(MAX_ETAG_RETRIES) {
                val state = states.get(profileId, namespace.wireName) ?: ProfileSyncStateEntity(profileId, namespace.wireName)
                val listing = objects.listObjects(remoteId)
                if (!listing.isSuccessful) return recordFailure(profileId, namespace, state, errors.from(listing))
                val metadata = listing.body()?.objects?.firstOrNull { it.namespace == namespace.wireName }
                if (metadata != null && (metadata.etag == null || metadata.schemaVersion == null)) return recordFailure(profileId, namespace, state, com.cstv.app.domain.model.CstvError.Incompatible)
                val remoteEtag = metadata?.etag?.let(CstvEtag::bare)
                if (remoteEtag != null && remoteEtag == state.etag && !state.pending) return
                var local = serializer.snapshot(profileId, namespace)
                if (remoteEtag != null && remoteEtag != state.etag) {
                    val response = objects.getObject(remoteId, namespace.wireName)
                    if (!response.isSuccessful) return recordFailure(profileId, namespace, state, errors.from(response))
                    val remoteBytes = response.body()?.bytes() ?: byteArrayOf()
                    val decoded = codec.decode(remoteBytes)
                    val remote = ((decoded as? SnapshotDecodeResult.Success)?.snapshot ?: return recordDecodeFailure(profileId, namespace, state, decoded))
                        .let(SnapshotUpgrader::upgrade)
                    // T20: the stored merge base can itself predate the v1->v2 format switch; it
                    // must be upgraded the same way the remote document is, or the merger compares
                    // a v1 key against a v2 key and duplicates every item.
                    val base = state.baseSnapshot?.let { bytes -> (codec.decode(bytes) as? SnapshotDecodeResult.Success)?.snapshot }
                        ?.let(SnapshotUpgrader::upgrade)
                    // T19-R1: the three-way merge is not itself bounded -- two disjoint local/remote
                    // sets can total up to 2N objects, and a remote object from an older client can
                    // still carry a stripped field (e.g. playback's `plot`). Re-apply the T19 cap and
                    // field stripping before this result reaches Room (`apply`) or the wire (`encode`).
                    local = merger.merge(base, local, remote, state.pending).normalized(namespace)
                    // A response that arrived after an account switch is never
                    // allowed to alter the newly selected account's Room data.
                    if (sessions.get()?.accountId != accountIdAtStart) return
                    serializer.apply(profileId, local)
                    if (local.objects == remote.objects) {
                        // Merged result matches what the server already has: nothing
                        // left to push, whether or not this namespace was pending.
                        states.upsert(state.copy(etag = remoteEtag, baseSnapshot = remoteBytes, lastSyncedAt = System.currentTimeMillis(), failureCode = null, pending = false))
                        mutableStatus.value = CloudSyncStatus.Idle
                        return
                    }
                    // The merge produced content the server doesn't have yet --
                    // typically objects that existed only in Room before this
                    // namespace's very first contact with the cloud. This must be
                    // pushed even when `pending` was false, otherwise those objects
                    // would stay forever confined to this installation's Room.
                }
                val encoded = codec.encode(local)
                if (encoded !is SnapshotEncodeResult.Success) return recordFailure(profileId, namespace, state, com.cstv.app.domain.model.CstvError.PayloadTooLarge)
                val body = encoded.bytes.toRequestBody(GZIP_MEDIA_TYPE)
                val put = objects.putObject(remoteId, namespace.wireName, SnapshotCodec.SCHEMA_VERSION, CstvEtag.header(remoteEtag ?: state.etag), body)
                if (put.isSuccessful) {
                    states.upsert(state.copy(etag = CstvEtag.bare(put.headers()["ETag"] ?: remoteEtag), baseSnapshot = encoded.bytes, pending = false, lastSyncedAt = System.currentTimeMillis(), lastAttemptAt = System.currentTimeMillis(), failureCode = null, retryCount = 0))
                    mutableStatus.value = CloudSyncStatus.Idle
                    return
                }
                if (put.code() != 428 && put.code() != 412) {
                    return recordFailure(profileId, namespace, state, errors.from(put))
                }
            }
            val current = states.get(profileId, namespace.wireName) ?: ProfileSyncStateEntity(profileId, namespace.wireName)
            states.upsert(current.copy(pending = true, failureCode = "ETAG_MISMATCH", retryCount = MAX_ETAG_RETRIES))
            mutableStatus.value = CloudSyncStatus.Failed("ETAG_MISMATCH")
        }
    }

    private suspend fun recordDecodeFailure(profileId: Int, namespace: SyncNamespace, state: ProfileSyncStateEntity, result: SnapshotDecodeResult) {
        val failure = when (result) { SnapshotDecodeResult.TooLarge -> "PAYLOAD_TOO_LARGE"; SnapshotDecodeResult.Incompatible -> "INCOMPATIBLE"; else -> "MALFORMED" }
        states.upsert(state.copy(pending = state.pending, lastAttemptAt = System.currentTimeMillis(), failureCode = failure))
        mutableStatus.value = if (failure == "INCOMPATIBLE") CloudSyncStatus.Incompatible else CloudSyncStatus.Failed(failure)
    }
    private suspend fun recordFailure(profileId: Int, namespace: SyncNamespace, state: ProfileSyncStateEntity, error: com.cstv.app.domain.model.CstvError) {
        val code = when (error) {
            com.cstv.app.domain.model.CstvError.ProfileNotFound -> "PROFILE_NOT_FOUND"
            com.cstv.app.domain.model.CstvError.PayloadTooLarge -> "PAYLOAD_TOO_LARGE"
            com.cstv.app.domain.model.CstvError.StorageQuota -> "STORAGE_QUOTA_EXCEEDED"
            com.cstv.app.domain.model.CstvError.NamespaceLimit -> "NAMESPACE_LIMIT_REACHED"
            com.cstv.app.domain.model.CstvError.Incompatible -> "INCOMPATIBLE"
            com.cstv.app.domain.model.CstvError.PreconditionRequired -> "PRECONDITION_REQUIRED"
            com.cstv.app.domain.model.CstvError.EtagMismatch -> "ETAG_MISMATCH"
            com.cstv.app.domain.model.CstvError.ServerError -> "SERVER_ERROR"
            else -> "UNAVAILABLE"
        }
        // Terminal failures where an identical retry cannot succeed until local/account state
        // changes: the object is not left pending so the worker stops re-pushing it in a loop.
        val terminal = error == com.cstv.app.domain.model.CstvError.ProfileNotFound ||
            error == com.cstv.app.domain.model.CstvError.PayloadTooLarge ||
            error == com.cstv.app.domain.model.CstvError.StorageQuota ||
            error == com.cstv.app.domain.model.CstvError.NamespaceLimit ||
            error == com.cstv.app.domain.model.CstvError.Incompatible
        states.upsert(state.copy(pending = !terminal, lastAttemptAt = System.currentTimeMillis(), failureCode = code, retryCount = state.retryCount + 1))
        mutableStatus.value = if (error == com.cstv.app.domain.model.CstvError.Incompatible) CloudSyncStatus.Incompatible else CloudSyncStatus.Failed(code)
    }
    private fun key(profileId: Int, namespace: SyncNamespace) = "$profileId:${namespace.wireName}"
    private fun mutex(profileId: Int, namespace: SyncNamespace): Mutex = synchronized(locks) { locks.getOrPut(key(profileId, namespace)) { Mutex() } }
    private fun kotlinx.coroutines.CoroutineScope.launchCatching(block: suspend () -> Unit) = launch { runCatching { block() } }
    private companion object { const val DEBOUNCE_MS = 2_000L; const val MAX_ETAG_RETRIES = 3; val GZIP_MEDIA_TYPE = "application/vnd.cstv.blob+gzip".toMediaType() }
}
