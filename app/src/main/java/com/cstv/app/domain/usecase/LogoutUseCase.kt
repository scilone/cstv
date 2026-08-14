package com.cstv.app.domain.usecase

import com.cstv.app.domain.repository.AuthRepository
import com.cstv.app.domain.repository.IptvCredentialsBackupRepository
import com.cstv.app.domain.model.IptvBackupOutcome
import com.cstv.app.domain.model.IptvCloudBackupNotice
import com.cstv.app.domain.model.IptvCloudBackupNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

class LogoutUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val backup: IptvCredentialsBackupRepository,
    private val notifier: IptvCloudBackupNotifier,
    @javax.inject.Named("applicationScope") private val applicationScope: CoroutineScope
) {
    operator fun invoke() {
        authRepository.clearCredentials()
        applicationScope.launch {
            if (backup.deleteForIptvLogout() == IptvBackupOutcome.Deferred) {
                notifier.publish(IptvCloudBackupNotice.DELETE_DEFERRED)
            }
        }
    }
}
