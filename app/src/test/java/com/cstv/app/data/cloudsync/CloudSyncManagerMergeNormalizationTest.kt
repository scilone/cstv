package com.cstv.app.data.cloudsync

import com.cstv.app.data.cloudsync.merge.SnapshotMerger
import com.cstv.app.data.local.dao.ProfileDao
import com.cstv.app.data.local.dao.ProfileSyncStateDao
import com.cstv.app.data.local.entity.ProfileEntity
import com.cstv.app.data.local.entity.ProfileSyncStateEntity
import com.cstv.app.data.local.storage.CstvSessionManager
import com.cstv.app.data.remote.CstvErrorMapper
import com.cstv.app.data.remote.api.CstvObjectsApiService
import com.cstv.app.data.remote.dto.ObjectListDto
import com.cstv.app.data.remote.dto.ObjectMetadataDto
import com.cstv.app.domain.model.CstvSession
import com.cstv.app.domain.network.NetworkMonitor
import com.cstv.app.domain.sync.SyncNamespace
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import retrofit2.Response

/**
 * T19-R1: proves the post-merge normalization is actually wired into CloudSyncManagerImpl, not
 * just implemented as a dangling pure function. A merged PLAYBACK snapshot must lose `plot` both
 * in what gets applied to Room and in the body that actually goes over the wire, even though
 * `RoomSnapshotSerializer` never touches this particular remote object -- it comes from the
 * "other device" side of the merge.
 */
class CloudSyncManagerMergeNormalizationTest {
    @get:Rule val globalTimeout: Timeout = Timeout.seconds(60)

    private val profileId = 7
    private val remoteId = "remote-profile-1"
    private val namespace = SyncNamespace.PLAYBACK
    private val otherNamespaces = SyncNamespace.entries.filterNot { it == namespace }

    @Mock private lateinit var profiles: ProfileDao
    @Mock private lateinit var states: ProfileSyncStateDao
    @Mock private lateinit var objects: CstvObjectsApiService
    @Mock private lateinit var network: NetworkMonitor
    @Mock private lateinit var sessions: CstvSessionManager

    private val gson = Gson()
    private lateinit var codec: SnapshotCodec
    private lateinit var serializer: FakePlaybackSerializer
    private lateinit var manager: CloudSyncManagerImpl

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        codec = SnapshotCodec(gson)
        serializer = FakePlaybackSerializer()
        whenever(network.isCurrentlyOnline()).thenReturn(true)
        whenever(sessions.get()).thenReturn(CstvSession("token", "account-1", "mail@example.test", Long.MAX_VALUE))
        manager = CloudSyncManagerImpl(
            profiles, states, objects, serializer, codec, SnapshotMerger(), CstvErrorMapper(gson), network, sessions,
            appContext = org.mockito.kotlin.mock(), scope = CoroutineScope(Dispatchers.Unconfined),
        )
    }

    private fun playbackItem(streamId: Int, lastAccessedAt: Long, plot: String?): com.google.gson.JsonElement {
        val map = mutableMapOf<String, Any>("streamId" to streamId, "lastAccessedAt" to lastAccessedAt)
        if (plot != null) map["plot"] = plot
        return gson.toJsonTree(map)
    }

    private fun bodyOf(snapshot: NamespaceSnapshot): okhttp3.ResponseBody {
        val bytes = (codec.encode(snapshot) as SnapshotEncodeResult.Success).bytes
        return bytes.toResponseBody("application/vnd.cstv.blob+gzip".toMediaType())
    }

    @Test
    fun `a remote playback object still carrying plot loses it after merge, both applied and pushed`() = runTest {
        val activeProfile = ProfileEntity(id = profileId, name = "P", avatarId = 0, createdAt = 0L, remoteId = remoteId)
        whenever(profiles.getById(profileId)).thenReturn(activeProfile)
        otherNamespaces.forEach { ns ->
            whenever(states.get(profileId, ns.wireName))
                .thenReturn(ProfileSyncStateEntity(profileId, ns.wireName, etag = "idle", pending = false))
        }
        val idle = otherNamespaces.map { ObjectMetadataDto(it.wireName, "idle", 1, 10, null) }
        whenever(objects.listObjects(remoteId)).thenReturn(
            Response.success(ObjectListDto(idle + ObjectMetadataDto(namespace.wireName, "remote-etag", 1, 10, null))),
        )
        // base == null: first-ever contact for this namespace, remote has an object our local
        // (empty) snapshot doesn't. `resolve()` with before == null keeps the remote side as-is,
        // `plot` included -- exactly the scenario T19-R1 flagged.
        whenever(states.get(profileId, namespace.wireName))
            .thenReturn(ProfileSyncStateEntity(profileId, namespace.wireName, etag = null, pending = false))
        val remote = NamespaceSnapshot(
            namespace.schemaVersion, namespace.wireName,
            mapOf("1" to playbackItem(1, lastAccessedAt = 100, plot = "an old client still sent this")),
        )
        whenever(objects.getObject(remoteId, namespace.wireName)).thenReturn(Response.success(bodyOf(remote)))
        serializer.snapshot = NamespaceSnapshot(namespace.schemaVersion, namespace.wireName, emptyMap())

        var pushedBody: okio.Buffer? = null
        whenever(objects.putObject(eq(remoteId), eq(namespace.wireName), any(), any(), any())).thenAnswer { invocation ->
            val body = invocation.getArgument<okhttp3.RequestBody>(4)
            val buffer = okio.Buffer()
            body.writeTo(buffer)
            pushedBody = buffer
            Response.success(Unit, okhttp3.Headers.headersOf("ETag", "\"merged-etag\""))
        }

        manager.synchronizeProfile(profileId)

        // Applied to Room: no `plot`, even though it was never touched by RoomSnapshotSerializer.
        val applied = serializer.applied
        assertEquals(setOf("1"), applied?.objects?.keys)
        assertFalse(applied!!.objects.getValue("1").asJsonObject.has("plot"))

        // Pushed to the server: same guarantee on the actual wire body, not just the in-memory value.
        val pushed = pushedBody
        org.junit.Assert.assertNotNull("putObject was never called", pushed)
        val decoded = codec.decode(pushed!!.readByteArray()) as SnapshotDecodeResult.Success
        assertFalse(decoded.snapshot.objects.getValue("1").asJsonObject.has("plot"))
    }
}

private class FakePlaybackSerializer : SnapshotSerializer {
    var snapshot: NamespaceSnapshot? = null
    var applied: NamespaceSnapshot? = null
    override suspend fun snapshot(profileId: Int, namespace: SyncNamespace): NamespaceSnapshot =
        snapshot ?: NamespaceSnapshot(namespace.schemaVersion, namespace.wireName, emptyMap())
    override suspend fun apply(profileId: Int, snapshot: NamespaceSnapshot) {
        applied = snapshot
    }
}
