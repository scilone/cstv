package com.cstv.app.domain.usecase

import com.cstv.app.domain.repository.CstvAuthRepository
import com.cstv.app.data.local.storage.CstvSessionManager
import javax.inject.Inject

class VerifyOtpUseCase @Inject constructor(
    private val repository: CstvAuthRepository,
    private val sessions: CstvSessionManager? = null,
    private val resetPlaybackLockUseCase: ResetPlaybackLockUseCase? = null,
) {
    suspend operator fun invoke(email: String, code: String) = run {
        val previousAccountId = sessions?.get()?.accountId
        repository.verifyOtp(email, code).also { result ->
        if (sessions != null && result.getOrNull()?.id != previousAccountId) resetPlaybackLockUseCase?.invoke()
        }
    }
}
