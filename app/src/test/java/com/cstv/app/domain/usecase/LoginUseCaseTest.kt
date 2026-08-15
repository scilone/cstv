package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.AutoLoginOutcome
import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.model.IptvBackupOutcome
import com.cstv.app.domain.model.IptvRestoreOutcome
import com.cstv.app.domain.model.IptvCloudBackupNotifier
import com.cstv.app.domain.model.UserInfo
import com.cstv.app.domain.repository.AuthRepository
import com.cstv.app.domain.repository.IptvCredentialsBackupRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginUseCaseTest {
    @Test fun successfulLoginAlwaysPersistsLocallyAndDefersCloudFailure() = runTest {
        val credentials = Credentials("panel", 8080, "user", "secret")
        val auth = FakeAuth()
        val backup = FakeBackup(IptvBackupOutcome.Deferred)
        val useCase = LoginUseCase(auth, backup, IptvCloudBackupNotifier(), CoroutineScope(StandardTestDispatcher(testScheduler)))

        useCase(credentials)
        assertEquals(credentials, auth.saved)
        advanceUntilIdle()
        assertEquals(credentials, backup.authenticated)
    }

    private class FakeAuth : AuthRepository {
        var saved: Credentials? = null
        override suspend fun login(credentials: Credentials) = UserInfo("user", true, "Active", "", 1, 0, "")
        override suspend fun autoLogin() = AutoLoginOutcome.NoCredentials
        override fun saveCredentials(credentials: Credentials) { saved = credentials }
        override fun getSavedCredentials() = saved
        override fun clearCredentials() = Unit
    }
    private class FakeBackup(private val result: IptvBackupOutcome) : IptvCredentialsBackupRepository {
        var authenticated: Credentials? = null
        override fun linkedAccountId() = "account"
        override fun isConsentEnabled() = true
        override suspend fun setConsent(enabled: Boolean) = IptvBackupOutcome.Skipped
        override suspend fun onAuthenticated(credentials: Credentials): IptvBackupOutcome { authenticated = credentials; return result }
        override suspend fun restore() = IptvRestoreOutcome.Absent
        override suspend fun deleteForIptvLogout() = IptvBackupOutcome.Skipped
        override suspend fun deleteForCstvSignOut() = IptvBackupOutcome.Skipped
        override suspend fun drainPending() = IptvBackupOutcome.Skipped
    }
}
