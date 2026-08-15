package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.AutoLoginOutcome
import com.cstv.app.domain.model.AutoLoginRejection
import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.model.IptvBackupOutcome
import com.cstv.app.domain.model.IptvRestoreOutcome
import com.cstv.app.domain.repository.AuthRepository
import com.cstv.app.domain.repository.IptvCredentialsBackupRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutoLoginUseCaseTest {
    @Test
    fun invalidLocalCredentialsNeverRevokeTheCloudBackup() = runTest {
        val backup = FakeBackup()
        val auth = FakeAuth(AutoLoginOutcome.Rejected(AutoLoginRejection.INVALID_CREDENTIALS, "invalid"))
        val dispatcher = StandardTestDispatcher(testScheduler)
        val useCase = AutoLoginUseCase(auth, RestoreIptvCredentialsUseCase(backup, auth), backup, CoroutineScope(dispatcher))

        // Un `auth != 1` du panel ne prouve pas que les identifiants sont
        // faux : il tombe aussi quand le compte est au plafond de connexions
        // simultanées. La sauvegarde ne se retire que sur décision explicite.
        assertEquals(AutoLoginOutcome.Rejected(AutoLoginRejection.INVALID_CREDENTIALS, "invalid"), useCase())
        advanceUntilIdle()
        assertEquals(null, backup.authenticated)
    }

    @Test
    fun onlineLocalCredentialsAreSyncedOutsideTheStartupPath() = runTest {
        val backup = FakeBackup()
        val credentials = Credentials("panel.example", 8080, "alice", "secret")
        val online = AutoLoginOutcome.Online(com.cstv.app.domain.model.UserInfo("alice", true, "Active", "Unlimited", 1, 0, ""))
        val auth = FakeAuth(online, credentials)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val useCase = AutoLoginUseCase(auth, RestoreIptvCredentialsUseCase(backup, auth), backup, CoroutineScope(dispatcher))

        assertEquals(online, useCase())
        assertEquals(null, backup.authenticated)
        advanceUntilIdle()
        assertEquals(credentials, backup.authenticated)
    }

    @Test
    fun offlineSessionDoesNotUploadAndLocalCredentialsNeverRestoreTheCloud() = runTest {
        val backup = FakeBackup()
        val offline = AutoLoginOutcome.OfflineSession(com.cstv.app.domain.model.UserInfo("alice", true, "Active", "", 1, 0, ""))
        val auth = FakeAuth(offline, Credentials("panel.example", 8080, "alice", "secret"))
        val dispatcher = StandardTestDispatcher(testScheduler)
        val useCase = AutoLoginUseCase(auth, RestoreIptvCredentialsUseCase(backup, auth), backup, CoroutineScope(dispatcher))

        assertEquals(offline, useCase())
        advanceUntilIdle()
        assertEquals(null, backup.authenticated)
        assertEquals(false, backup.restored)
    }

    private class FakeAuth(
        private val outcome: AutoLoginOutcome,
        private val credentials: Credentials? = null
    ) : AuthRepository {
        override suspend fun login(credentials: Credentials): com.cstv.app.domain.model.UserInfo = error("Not used")
        override suspend fun autoLogin(): AutoLoginOutcome = outcome
        override fun saveCredentials(credentials: Credentials) = Unit
        override fun getSavedCredentials(): Credentials? = credentials
        override fun clearCredentials() = Unit
    }

    private class FakeBackup : IptvCredentialsBackupRepository {
        var authenticated: Credentials? = null
        var restored = false
        override fun linkedAccountId(): String? = null
        override fun isConsentEnabled(): Boolean = false
        override suspend fun setConsent(enabled: Boolean): IptvBackupOutcome = IptvBackupOutcome.Skipped
        override suspend fun onAuthenticated(credentials: Credentials): IptvBackupOutcome { authenticated = credentials; return IptvBackupOutcome.Skipped }
        override suspend fun restore(): IptvRestoreOutcome { restored = true; return IptvRestoreOutcome.Absent }
        override suspend fun deleteForIptvLogout(): IptvBackupOutcome = IptvBackupOutcome.Skipped
        override suspend fun deleteForCstvSignOut(): IptvBackupOutcome = IptvBackupOutcome.Skipped
        override suspend fun drainPending(): IptvBackupOutcome = IptvBackupOutcome.Skipped
    }
}
