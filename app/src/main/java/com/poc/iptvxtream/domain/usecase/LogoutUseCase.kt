package com.poc.iptvxtream.domain.usecase

import com.poc.iptvxtream.domain.repository.AuthRepository
import javax.inject.Inject

class LogoutUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    operator fun invoke() {
        authRepository.clearCredentials()
    }
}
