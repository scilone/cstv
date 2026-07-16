package com.poc.iptvxtream.domain.repository

import com.poc.iptvxtream.domain.model.Credentials
import com.poc.iptvxtream.domain.model.UserInfo

interface AuthRepository {
    suspend fun login(credentials: Credentials): UserInfo
    fun saveCredentials(credentials: Credentials)
    suspend fun getSavedCredentials(): Credentials?
    fun clearCredentials()
}
