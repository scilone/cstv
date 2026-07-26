package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.AutoLoginOutcome
import com.cstv.app.domain.repository.AuthRepository
import javax.inject.Inject

class AutoLoginUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): AutoLoginOutcome = authRepository.autoLogin()
}
