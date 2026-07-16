package com.poc.iptvxtream.domain.usecase

import com.poc.iptvxtream.domain.model.Credentials
import com.poc.iptvxtream.domain.repository.AuthRepository
import javax.inject.Inject

class GetSavedCredentialsUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    operator fun invoke(): Credentials? {
        return authRepository.getSavedCredentials()
    }
}
