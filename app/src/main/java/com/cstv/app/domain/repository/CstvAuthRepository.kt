package com.cstv.app.domain.repository

import com.cstv.app.domain.model.*
import kotlinx.coroutines.flow.StateFlow

interface CstvAuthRepository {
    val sessionState: StateFlow<CstvSessionState>
    suspend fun requestOtp(email: String): Result<Unit>
    suspend fun verifyOtp(email: String, code: String): Result<CstvAccount>
    suspend fun resolveSession(): CstvSessionState
    fun signOut()
    fun storedEmail(): String?
}
