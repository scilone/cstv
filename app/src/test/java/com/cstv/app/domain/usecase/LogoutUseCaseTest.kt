package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.AutoLoginOutcome
import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.model.IptvBackupOutcome
import com.cstv.app.domain.model.IptvRestoreOutcome
import com.cstv.app.domain.model.IptvCloudBackupNotifier
import com.cstv.app.domain.repository.AuthRepository
import com.cstv.app.domain.repository.IptvCredentialsBackupRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LogoutUseCaseTest {
    @Test fun logoutClearsLocalCredentialsEvenWhenCloudDeletionIsDeferred() = runTest {
        val auth = FakeAuth()
        val backup = FakeBackup()
        LogoutUseCase(auth, backup, IptvCloudBackupNotifier(), CoroutineScope(StandardTestDispatcher(testScheduler)))()
        assertTrue(auth.cleared)
        advanceUntilIdle()
        assertTrue(backup.deleted)
    }
    private class FakeAuth : AuthRepository {
        var cleared = false
        override suspend fun login(credentials: Credentials) = error("unused")
        override suspend fun autoLogin() = AutoLoginOutcome.NoCredentials
        override fun saveCredentials(credentials: Credentials) = Unit
        override fun getSavedCredentials(): Credentials? = null
        override fun clearCredentials() { cleared = true }
    }
    private class FakeBackup : IptvCredentialsBackupRepository {
        var deleted = false
        override fun linkedAccountId(): String? = "account"
        override fun isConsentEnabled() = true
        override suspend fun setConsent(enabled: Boolean) = IptvBackupOutcome.Skipped
        override suspend fun onAuthenticated(credentials: Credentials) = IptvBackupOutcome.Skipped
        override suspend fun restore() = IptvRestoreOutcome.Absent
        override suspend fun deleteForIptvLogout(): IptvBackupOutcome { deleted = true; return IptvBackupOutcome.Deferred }
        override suspend fun deleteForCstvSignOut() = IptvBackupOutcome.Skipped
        override suspend fun drainPending() = IptvBackupOutcome.Skipped
    }
}
