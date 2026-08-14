package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.model.IptvBackupOutcome
import com.cstv.app.domain.model.IptvRestoreOutcome
import com.cstv.app.domain.repository.CstvAuthRepository
import com.cstv.app.domain.repository.IptvCredentialsBackupRepository
import com.cstv.app.domain.model.CstvSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SignOutCstvUseCaseTest {
    @Test fun cloudBackupIsDeletedBeforeCstvSessionIsClosed() = runTest {
        val calls = mutableListOf<String>()
        SignOutCstvUseCase(Backup(calls), Cstv(calls))()
        assertEquals(listOf("delete", "signOut"), calls)
    }
    private class Backup(private val calls: MutableList<String>) : IptvCredentialsBackupRepository {
        override fun linkedAccountId(): String? = "account"
        override fun isConsentEnabled() = true
        override suspend fun setConsent(enabled: Boolean) = IptvBackupOutcome.Skipped
        override suspend fun onAuthenticated(credentials: Credentials) = IptvBackupOutcome.Skipped
        override suspend fun restore() = IptvRestoreOutcome.Absent
        override suspend fun invalidateRestored() = IptvBackupOutcome.Skipped
        override suspend fun deleteForIptvLogout() = IptvBackupOutcome.Skipped
        override suspend fun deleteForCstvSignOut(): IptvBackupOutcome { calls += "delete"; return IptvBackupOutcome.Deleted }
        override suspend fun drainPending() = IptvBackupOutcome.Skipped
    }
    private class Cstv(private val calls: MutableList<String>) : CstvAuthRepository {
        override val sessionState = MutableStateFlow<CstvSessionState>(CstvSessionState.SignedOut())
        override suspend fun requestOtp(email: String): Result<Unit> = Result.failure(IllegalStateException())
        override suspend fun verifyOtp(email: String, code: String): Result<com.cstv.app.domain.model.CstvAccount> = Result.failure(IllegalStateException())
        override suspend fun resolveSession() = CstvSessionState.SignedOut()
        override fun storedEmail(): String? = null
        override fun signOut() { calls += "signOut" }
    }
}
