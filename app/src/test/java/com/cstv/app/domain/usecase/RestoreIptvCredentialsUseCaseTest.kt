package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.AccountExpiredException
import com.cstv.app.domain.model.AutoLoginOutcome
import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.model.InvalidCredentialsException
import com.cstv.app.domain.model.IptvBackupOutcome
import com.cstv.app.domain.model.IptvRestoreOutcome
import com.cstv.app.domain.model.UserInfo
import com.cstv.app.domain.repository.AuthRepository
import com.cstv.app.domain.repository.IptvCredentialsBackupRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreIptvCredentialsUseCaseTest {
    private val credentials = Credentials("panel.example", 8080, "alice", "secret")

    @Test
    fun absentBackupLeavesTheLoginFormPathUntouched() = runTest {
        val backup = FakeBackup(IptvRestoreOutcome.Absent)

        assertEquals(AutoLoginOutcome.NoCredentials, RestoreIptvCredentialsUseCase(backup, FakeAuth())())
    }

    @Test
    fun validBackupLogsInAndStoresTheCredentialsLocally() = runTest {
        val backup = FakeBackup(IptvRestoreOutcome.Restored(credentials))
        val auth = FakeAuth()

        val outcome = RestoreIptvCredentialsUseCase(backup, auth)()

        assertTrue(outcome is AutoLoginOutcome.Online)
        assertEquals(credentials, auth.saved)
    }

    @Test
    fun invalidRestoredCredentialsNeverRevokeTheBackup() = runTest {
        val backup = FakeBackup(IptvRestoreOutcome.Restored(credentials))
        val auth = FakeAuth(loginFailure = InvalidCredentialsException("bad credentials"))

        val outcome = RestoreIptvCredentialsUseCase(backup, auth)()

        // Voir AutoLoginUseCaseTest : le refus du panel est signalé à l'écran,
        // la sauvegarde reste en place.
        assertEquals(AutoLoginOutcome.Rejected(com.cstv.app.domain.model.AutoLoginRejection.CLOUD_CREDENTIALS_INVALID, ""), outcome)
    }

    @Test
    fun expiredAccountIsReportedWithoutTouchingTheBackup() = runTest {
        val backup = FakeBackup(IptvRestoreOutcome.Restored(credentials))
        val auth = FakeAuth(loginFailure = AccountExpiredException("expired", "01/01/2026"))

        val outcome = RestoreIptvCredentialsUseCase(backup, auth)()

        assertTrue(outcome is AutoLoginOutcome.Rejected)
    }

    @Test
    fun unreadableAndUnavailableBackupsNeverReachTheAuthRepository() = runTest {
        listOf(IptvRestoreOutcome.Unreadable, IptvRestoreOutcome.Unavailable).forEach { restore ->
            val auth = FakeAuth()
            val outcome = RestoreIptvCredentialsUseCase(FakeBackup(restore), auth)()
            assertTrue(outcome is AutoLoginOutcome.Rejected)
            assertEquals(null, auth.loginCredentials)
        }
    }

    private class FakeAuth(private val loginFailure: Exception? = null) : AuthRepository {
        var saved: Credentials? = null
        var loginCredentials: Credentials? = null
        override suspend fun login(credentials: Credentials): UserInfo {
            loginCredentials = credentials
            loginFailure?.let { throw it }
            return UserInfo(credentials.username, true, "Active", "Unlimited", 1, 0, "")
        }
        override suspend fun autoLogin(): AutoLoginOutcome = AutoLoginOutcome.NoCredentials
        override fun saveCredentials(credentials: Credentials) { saved = credentials }
        override fun getSavedCredentials(): Credentials? = saved
        override fun clearCredentials() { saved = null }
    }

    private class FakeBackup(private val restoreOutcome: IptvRestoreOutcome) : IptvCredentialsBackupRepository {
        override fun linkedAccountId(): String? = "account"
        override fun isConsentEnabled(): Boolean = false
        override suspend fun setConsent(enabled: Boolean): IptvBackupOutcome = IptvBackupOutcome.Skipped
        override suspend fun onAuthenticated(credentials: Credentials): IptvBackupOutcome = IptvBackupOutcome.Skipped
        override suspend fun restore(): IptvRestoreOutcome = restoreOutcome
        override suspend fun deleteForIptvLogout(): IptvBackupOutcome = IptvBackupOutcome.Skipped
        override suspend fun deleteForCstvSignOut(): IptvBackupOutcome = IptvBackupOutcome.Skipped
        override suspend fun drainPending(): IptvBackupOutcome = IptvBackupOutcome.Skipped
    }
}
