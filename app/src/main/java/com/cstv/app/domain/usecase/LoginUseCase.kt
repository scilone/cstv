package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.model.UserInfo
import com.cstv.app.domain.repository.AuthRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(credentials: Credentials): UserInfo {
        val userInfo = authRepository.login(credentials)
        if (credentials.rememberMe) {
            authRepository.saveCredentials(credentials)
        } else {
            authRepository.clearCredentials()
        }
        return userInfo
    }
}
