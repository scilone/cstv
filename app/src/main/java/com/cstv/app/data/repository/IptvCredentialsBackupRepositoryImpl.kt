package com.cstv.app.data.repository

import com.cstv.app.data.local.storage.CstvSessionManager
import com.cstv.app.data.local.storage.IptvCloudBackupStore
import com.cstv.app.data.remote.api.CstvIptvCredentialsApiService
import com.cstv.app.data.remote.dto.IptvCredentialsDto
import com.cstv.app.data.remote.dto.IptvCredentialsRequestDto
import com.cstv.app.data.worker.IptvCredentialsBackupScheduler
import com.cstv.app.domain.model.*
import com.cstv.app.domain.repository.IptvCredentialsBackupRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class IptvCredentialsBackupRepositoryImpl @Inject constructor(
    private val api: CstvIptvCredentialsApiService,
    private val sessions: CstvSessionManager,
    private val store: IptvCloudBackupStore,
    private val scheduler: IptvCredentialsBackupScheduler
) : IptvCredentialsBackupRepository {
    private val mutex = Mutex()

    override fun linkedAccountId(): String? = sessions.get()?.accountId
    override fun isConsentEnabled(): Boolean = store.resetForAccount(linkedAccountId()).consent
    override suspend fun setConsent(enabled: Boolean): IptvBackupOutcome = mutex.withLock {
        val accountId = linkedAccountId() ?: return@withLock IptvBackupOutcome.Skipped
        val state = store.resetForAccount(accountId)
        store.save(state.copy(
            consent = enabled,
            pendingOp = if (enabled) state.pendingOp else PendingCloudOp.DELETE,
            credentials = if (enabled) state.credentials else null
        ))
        if (enabled) IptvBackupOutcome.Skipped else drainPendingLocked()
    }

    override suspend fun onAuthenticated(credentials: Credentials): IptvBackupOutcome = mutex.withLock {
        val accountId = linkedAccountId() ?: return@withLock IptvBackupOutcome.Skipped
        var state = store.resetForAccount(accountId)
        if (!state.consent) return@withLock IptvBackupOutcome.Skipped
        if (state.pendingOp == PendingCloudOp.DELETE || state.pendingOp == PendingCloudOp.DELETE_IF_MATCH) {
            drainPendingLocked()
            if (store.get().pendingOp != PendingCloudOp.NONE) return@withLock IptvBackupOutcome.Deferred
            // Re-authentication is an explicit new consented save, after the
            // previously requested revocation has actually completed.
            state = store.get().copy(consent = true)
        }
        store.save(state.copy(credentials = credentials, pendingOp = PendingCloudOp.UPLOAD))
        drainPendingLocked()
    }

    override suspend fun restore(): IptvRestoreOutcome = mutex.withLock {
        val accountId = linkedAccountId() ?: return@withLock IptvRestoreOutcome.Absent
        val state = store.resetForAccount(accountId)
        if (state.pendingOp == PendingCloudOp.DELETE || state.pendingOp == PendingCloudOp.DELETE_IF_MATCH) {
            drainPendingLocked()
            return@withLock IptvRestoreOutcome.Absent
        }
        try {
            val response = api.get()
            when (response.code()) {
                200 -> response.body()?.let { dto ->
                    if (dto.host.isBlank() || dto.username.isBlank() || dto.password.isBlank() || dto.port !in 1..65535) return@let IptvRestoreOutcome.Unreadable
                    store.save(state.copy(consent = true, lastEtag = response.headers()["ETag"]?.trim('"'), pendingOp = PendingCloudOp.NONE))
                    IptvRestoreOutcome.Restored(Credentials(dto.host, dto.port, dto.username, dto.password))
                } ?: IptvRestoreOutcome.Unreadable
                404 -> IptvRestoreOutcome.Absent
                422 -> IptvRestoreOutcome.Unreadable
                else -> IptvRestoreOutcome.Unavailable
            }
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            IptvRestoreOutcome.Unavailable
        }
    }

    override suspend fun invalidateRestored(): IptvBackupOutcome = mutex.withLock {
        val state = store.resetForAccount(linkedAccountId())
        val etag = state.lastEtag ?: return@withLock IptvBackupOutcome.Skipped
        try {
            val response = api.delete("\"$etag\"")
            if (response.isSuccessful || response.code() == 412) {
                store.save(state.copy(consent = false, lastEtag = null, pendingOp = PendingCloudOp.NONE))
                IptvBackupOutcome.Deleted
            } else {
                deferConditionalDelete(state)
            }
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            deferConditionalDelete(state)
        }
    }
    override suspend fun deleteForIptvLogout(): IptvBackupOutcome = mutex.withLock {
        deleteUnconditionally(store.resetForAccount(linkedAccountId()))
    }
    override suspend fun deleteForCstvSignOut(): IptvBackupOutcome = mutex.withLock {
        deleteUnconditionally(store.resetForAccount(linkedAccountId()))
    }
    override suspend fun drainPending(): IptvBackupOutcome = mutex.withLock { drainPendingLocked() }

    private suspend fun drainPendingLocked(): IptvBackupOutcome {
        val state = store.resetForAccount(linkedAccountId())
        return when (state.pendingOp) {
            PendingCloudOp.NONE -> IptvBackupOutcome.Skipped
            PendingCloudOp.DELETE -> deleteUnconditionally(state)
            PendingCloudOp.DELETE_IF_MATCH -> deleteConditionally(state)
            PendingCloudOp.UPLOAD -> upload(state)
        }
    }

    private suspend fun upload(state: IptvCloudBackupState): IptvBackupOutcome {
        val credentials = state.credentials ?: run {
            store.save(state.copy(pendingOp = PendingCloudOp.NONE))
            return IptvBackupOutcome.Skipped
        }
        return try {
            val response = api.put(IptvCredentialsRequestDto(credentials.host, credentials.port, credentials.username, credentials.password))
            if (response.isSuccessful) {
                store.save(state.copy(lastEtag = response.headers()["ETag"]?.trim('"'), pendingOp = PendingCloudOp.NONE))
                IptvBackupOutcome.Saved
            } else deferUpload(state)
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            deferUpload(state)
        }
    }

    private suspend fun deleteUnconditionally(state: IptvCloudBackupState = store.get()): IptvBackupOutcome {
        if (state.accountId == null) return IptvBackupOutcome.Skipped
        return try {
            val response = api.delete()
            if (response.isSuccessful) {
                store.save(state.copy(consent = false, lastEtag = null, pendingOp = PendingCloudOp.NONE, credentials = null))
                IptvBackupOutcome.Deleted
            } else deferDelete(state)
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            deferDelete(state)
        }
    }

    private suspend fun deleteConditionally(state: IptvCloudBackupState): IptvBackupOutcome {
        val etag = state.lastEtag ?: return IptvBackupOutcome.Skipped
        return try {
            val response = api.delete("\"$etag\"")
            if (response.isSuccessful || response.code() == 412) {
                store.save(state.copy(consent = false, lastEtag = null, pendingOp = PendingCloudOp.NONE, credentials = null))
                IptvBackupOutcome.Deleted
            } else {
                deferConditionalDelete(state)
            }
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            deferConditionalDelete(state)
        }
    }

    private fun deferUpload(state: IptvCloudBackupState): IptvBackupOutcome {
        store.save(state.copy(pendingOp = PendingCloudOp.UPLOAD))
        scheduler.enqueue()
        return IptvBackupOutcome.Deferred
    }

    private fun deferDelete(state: IptvCloudBackupState): IptvBackupOutcome {
        store.save(state.copy(consent = false, pendingOp = PendingCloudOp.DELETE, credentials = null))
        scheduler.enqueue()
        return IptvBackupOutcome.Deferred
    }

    private fun deferConditionalDelete(state: IptvCloudBackupState): IptvBackupOutcome {
        store.save(state.copy(consent = false, pendingOp = PendingCloudOp.DELETE_IF_MATCH, credentials = null))
        scheduler.enqueue()
        return IptvBackupOutcome.Deferred
    }
}
