package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.model.IptvBackupOutcome
import com.cstv.app.domain.model.IptvRestoreOutcome
import com.cstv.app.domain.repository.IptvCredentialsBackupRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SetIptvCloudBackupConsentUseCaseTest {
    @Test fun changingConsentDelegatesWithoutTouchingLocalCredentials() = runTest {
        val backup = FakeBackup()
        assertEquals(IptvBackupOutcome.Deleted, SetIptvCloudBackupConsentUseCase(backup)(false))
        assertEquals(listOf(false), backup.values)
    }
    private class FakeBackup : IptvCredentialsBackupRepository {
        val values = mutableListOf<Boolean>()
        override fun linkedAccountId(): String? = "account"
        override fun isConsentEnabled() = true
        override suspend fun setConsent(enabled: Boolean): IptvBackupOutcome { values += enabled; return IptvBackupOutcome.Deleted }
        override suspend fun onAuthenticated(credentials: Credentials) = IptvBackupOutcome.Skipped
        override suspend fun restore() = IptvRestoreOutcome.Absent
        override suspend fun invalidateRestored() = IptvBackupOutcome.Skipped
        override suspend fun deleteForIptvLogout() = IptvBackupOutcome.Skipped
        override suspend fun deleteForCstvSignOut() = IptvBackupOutcome.Skipped
        override suspend fun drainPending() = IptvBackupOutcome.Skipped
    }
}
