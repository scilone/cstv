package com.cstv.app.domain.usecase

import com.cstv.app.domain.repository.AuthRepository
import javax.inject.Inject

class LogoutUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    operator fun invoke() {
        authRepository.clearCredentials()
    }
}
