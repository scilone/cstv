package com.cstv.app.domain.repository

import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.model.UserInfo

interface AuthRepository {
    suspend fun login(credentials: Credentials): UserInfo
    fun saveCredentials(credentials: Credentials)
    fun getSavedCredentials(): Credentials?
    fun clearCredentials()
}
