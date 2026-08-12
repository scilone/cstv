package com.cstv.app.domain.usecase

import com.cstv.app.domain.repository.CstvAuthRepository
import javax.inject.Inject

class ResolveCstvSessionUseCase @Inject constructor(private val repository: CstvAuthRepository) {
    suspend operator fun invoke() = repository.resolveSession()
}
