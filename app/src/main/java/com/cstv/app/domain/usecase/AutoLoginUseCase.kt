package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.AutoLoginOutcome
import com.cstv.app.domain.model.AutoLoginRejection
import com.cstv.app.domain.repository.AuthRepository
import com.cstv.app.domain.repository.IptvCredentialsBackupRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

class AutoLoginUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val restoreIptvCredentials: RestoreIptvCredentialsUseCase,
    private val backup: IptvCredentialsBackupRepository,
    @javax.inject.Named("applicationScope") private val applicationScope: CoroutineScope
) {
    suspend operator fun invoke(): AutoLoginOutcome {
        val result = authRepository.autoLogin()
        return when (result) {
            AutoLoginOutcome.NoCredentials -> restoreIptvCredentials()
            is AutoLoginOutcome.Online -> {
                authRepository.getSavedCredentials()?.let { credentials ->
                    applicationScope.launch { backup.onAuthenticated(credentials) }
                }
                result
            }
            is AutoLoginOutcome.Rejected -> {
                if (result.reason == AutoLoginRejection.INVALID_CREDENTIALS) {
                    applicationScope.launch { backup.invalidateRestored() }
                }
                result
            }
            else -> result
        }
    }
}
