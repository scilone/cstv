package com.cstv.app.data.repository

import com.cstv.app.data.local.storage.CstvSessionManager
import com.cstv.app.data.local.storage.IptvCloudBackupStore
import com.cstv.app.data.remote.api.CstvIptvCredentialsApiService
import com.cstv.app.data.remote.dto.IptvCredentialsDto
import com.cstv.app.data.remote.dto.IptvCredentialsRequestDto
import com.cstv.app.data.worker.IptvCredentialsBackupScheduler
import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.model.CstvSession
import com.cstv.app.domain.model.IptvBackupOutcome
import com.cstv.app.domain.model.IptvCloudBackupState
import com.cstv.app.domain.model.IptvRestoreOutcome
import com.cstv.app.domain.model.PendingCloudOp
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response

class IptvCredentialsBackupRepositoryImplTest {
    private val accountA = "account-a"
    private val accountB = "account-b"
    private val credentials = Credentials("panel.example", 8080, "alice", "secret")

    @Test
    fun disabledConsentDoesNotCallTheCloud() = runTest {
        val fixture = fixture(IptvCloudBackupState(accountId = accountA, consent = false))

        assertEquals(IptvBackupOutcome.Skipped, fixture.repository.onAuthenticated(credentials))

        verify(fixture.api, never()).put(any(), any())
    }

    @Test
    fun enabledConsentUploadsAndStoresEtag() = runTest {
        val fixture = fixture(IptvCloudBackupState(accountId = accountA, consent = true))
        whenever(fixture.api.put(IptvCredentialsRequestDto("panel.example", 8080, "alice", "secret"), null))
            .thenReturn(Response.success(Unit, okhttp3.Headers.headersOf("ETag", "\"etag-a\"")))

        assertEquals(IptvBackupOutcome.Saved, fixture.repository.onAuthenticated(credentials))

        assertEquals(PendingCloudOp.NONE, fixture.store.state.pendingOp)
        assertEquals("etag-a", fixture.store.state.lastEtag)
    }

    @Test
    fun uploadFailureIsDeferredWithoutLeakingAnException() = runTest {
        val fixture = fixture(IptvCloudBackupState(accountId = accountA, consent = true))
        whenever(fixture.api.put(any(), any())).thenThrow(RuntimeException("offline"))

        assertEquals(IptvBackupOutcome.Deferred, fixture.repository.onAuthenticated(credentials))

        assertEquals(PendingCloudOp.UPLOAD, fixture.store.state.pendingOp)
        assertTrue(fixture.scheduler.enqueued)
    }

    @Test
    fun pendingDeleteIsDrainedBeforeACloudRestore() = runTest {
        val fixture = fixture(IptvCloudBackupState(accountId = accountA, pendingOp = PendingCloudOp.DELETE))
        whenever(fixture.api.delete(null)).thenReturn(Response.success(Unit))

        assertEquals(IptvRestoreOutcome.Absent, fixture.repository.restore())

        verify(fixture.api).delete(null)
        verify(fixture.api, never()).get()
        assertEquals(PendingCloudOp.NONE, fixture.store.state.pendingOp)
    }

    @Test
    fun pendingDeleteIsDrainedBeforeANewUpload() = runTest {
        val fixture = fixture(IptvCloudBackupState(accountId = accountA, consent = true, pendingOp = PendingCloudOp.DELETE))
        whenever(fixture.api.delete(null)).thenReturn(Response.success(Unit))
        whenever(fixture.api.put(IptvCredentialsRequestDto("panel.example", 8080, "alice", "secret"), null)).thenReturn(Response.success(Unit))

        assertEquals(IptvBackupOutcome.Saved, fixture.repository.onAuthenticated(credentials))

        verify(fixture.api).delete(null)
        verify(fixture.api).put(IptvCredentialsRequestDto("panel.example", 8080, "alice", "secret"), null)
    }

    /**
     * `DELETE_IF_MATCH` n'est plus produit par l'application : la révocation
     * automatique sur identifiants refusés a été retirée (voir
     * `AutoLoginUseCase`). Une installation mise à jour peut en revanche porter
     * une révocation différée décidée avant ce changement — `drainPending` doit
     * donc continuer de la mener à terme, ce que couvrent ces deux cas.
     */
    @Test
    fun pendingConditionalDeleteTreatsAChangedRemoteCopyAsSuccess() = runTest {
        val fixture = fixture(
            IptvCloudBackupState(accountId = accountA, consent = true, lastEtag = "known", pendingOp = PendingCloudOp.DELETE_IF_MATCH)
        )
        whenever(fixture.api.delete("\"known\""))
            .thenReturn(Response.error(412, "{}".toResponseBody()))

        assertEquals(IptvBackupOutcome.Deleted, fixture.repository.drainPending())

        assertFalse(fixture.store.state.consent)
        assertEquals(PendingCloudOp.NONE, fixture.store.state.pendingOp)
    }

    @Test
    fun deferredConditionalDeleteKeepsItsEtag() = runTest {
        val fixture = fixture(
            IptvCloudBackupState(accountId = accountA, consent = true, lastEtag = "known", pendingOp = PendingCloudOp.DELETE_IF_MATCH)
        )
        whenever(fixture.api.delete("\"known\"")).thenThrow(RuntimeException("offline")).thenReturn(Response.success(Unit))

        assertEquals(IptvBackupOutcome.Deferred, fixture.repository.drainPending())
        assertEquals(PendingCloudOp.DELETE_IF_MATCH, fixture.store.state.pendingOp)

        assertEquals(IptvBackupOutcome.Deleted, fixture.repository.drainPending())
        verify(fixture.api, org.mockito.kotlin.times(2)).delete("\"known\"")
    }

    @Test
    fun changedCstvAccountResetsOldStateWithoutCallingTheOldAccount() = runTest {
        val fixture = fixture(IptvCloudBackupState(accountId = accountA, consent = true, pendingOp = PendingCloudOp.UPLOAD), accountB)

        assertEquals(IptvBackupOutcome.Skipped, fixture.repository.drainPending())

        assertEquals(accountB, fixture.store.state.accountId)
        assertFalse(fixture.store.state.consent)
        verify(fixture.api, never()).put(any(), any())
    }

    private fun fixture(initial: IptvCloudBackupState, accountId: String = accountA): Fixture {
        val api = mock<CstvIptvCredentialsApiService>()
        val sessions = mock<CstvSessionManager> {
            on { get() } doReturn CstvSession("token", accountId, "user@example.com", 0)
        }
        val store = MemoryStore(initial)
        val scheduler = RecordingScheduler()
        return Fixture(
            api,
            store,
            scheduler,
            IptvCredentialsBackupRepositoryImpl(api, sessions, store, scheduler)
        )
    }

    private data class Fixture(
        val api: CstvIptvCredentialsApiService,
        val store: MemoryStore,
        val scheduler: RecordingScheduler,
        val repository: IptvCredentialsBackupRepositoryImpl
    )

    private class RecordingScheduler : IptvCredentialsBackupScheduler {
        var enqueued = false
        override fun enqueue() { enqueued = true }
    }

    private class MemoryStore(initial: IptvCloudBackupState) : IptvCloudBackupStore {
        var state = initial
        override fun get(): IptvCloudBackupState = state
        override fun save(state: IptvCloudBackupState) { this.state = state }
        override fun resetForAccount(accountId: String?): IptvCloudBackupState {
            if (state.accountId != accountId) state = IptvCloudBackupState(accountId = accountId)
            return state
        }
    }
}
