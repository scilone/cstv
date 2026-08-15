package com.cstv.app.domain.repository

import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.model.IptvBackupOutcome
import com.cstv.app.domain.model.IptvRestoreOutcome

interface IptvCredentialsBackupRepository {
    fun linkedAccountId(): String?
    fun isConsentEnabled(): Boolean
    suspend fun setConsent(enabled: Boolean): IptvBackupOutcome
    suspend fun onAuthenticated(credentials: Credentials): IptvBackupOutcome
    suspend fun restore(): IptvRestoreOutcome
    suspend fun deleteForIptvLogout(): IptvBackupOutcome
    suspend fun deleteForCstvSignOut(): IptvBackupOutcome
    suspend fun drainPending(): IptvBackupOutcome
}
