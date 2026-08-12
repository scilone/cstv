package com.cstv.app.domain.usecase

import com.cstv.app.domain.repository.CstvAuthRepository
import javax.inject.Inject

class RequestOtpUseCase @Inject constructor(private val repository: CstvAuthRepository) {
    suspend operator fun invoke(email: String) = repository.requestOtp(email)
}
