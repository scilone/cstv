package com.cstv.app.domain.usecase

import com.cstv.app.domain.repository.CstvAuthRepository
import javax.inject.Inject

class VerifyOtpUseCase @Inject constructor(private val repository: CstvAuthRepository) {
    suspend operator fun invoke(email: String, code: String) = repository.verifyOtp(email, code)
}
