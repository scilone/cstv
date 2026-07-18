package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.repository.AuthRepository
import javax.inject.Inject

class GetSavedCredentialsUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    operator fun invoke(): Credentials? {
        return authRepository.getSavedCredentials()
    }
}
