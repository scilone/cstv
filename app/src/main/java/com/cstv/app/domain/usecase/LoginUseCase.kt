package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.model.UserInfo
import com.cstv.app.domain.model.IptvBackupOutcome
import com.cstv.app.domain.model.IptvCloudBackupNotice
import com.cstv.app.domain.model.IptvCloudBackupNotifier
import com.cstv.app.domain.repository.AuthRepository
import com.cstv.app.domain.repository.IptvCredentialsBackupRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val backupRepository: IptvCredentialsBackupRepository,
    private val notifier: IptvCloudBackupNotifier,
    @javax.inject.Named("applicationScope") private val applicationScope: CoroutineScope
) {
    suspend operator fun invoke(credentials: Credentials): UserInfo {
        val userInfo = authRepository.login(credentials)
        authRepository.saveCredentials(credentials)
        applicationScope.launch {
            if (backupRepository.onAuthenticated(credentials) == IptvBackupOutcome.Deferred) {
                notifier.publish(IptvCloudBackupNotice.SAVE_DEFERRED)
            }
        }
        return userInfo
    }
}
