package com.cstv.app.data.repository

import com.cstv.app.data.remote.CstvErrorMapper
import com.cstv.app.data.remote.api.CstvApiService
import com.cstv.app.data.remote.dto.CstvProfileCreateDto
import com.cstv.app.data.remote.dto.CstvProfileUpdateDto
import com.cstv.app.domain.model.CstvProfile
import com.cstv.app.domain.model.CstvTimestampParser
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CstvProfileGateway @Inject constructor(private val api: CstvApiService, private val errors: CstvErrorMapper) {
    suspend fun create(name: String, avatarId: Int): CstvProfile = unavailableOnIo {
        val response = api.createProfile(CstvProfileCreateDto(name, avatarId))
        response.body()?.takeIf { response.isSuccessful }?.toDomain() ?: throw CstvException(errors.from(response))
    }
    suspend fun update(id: String, name: String? = null, avatarId: Int? = null): CstvProfile = unavailableOnIo {
        val response = api.updateProfile(id, CstvProfileUpdateDto(name, avatarId))
        response.body()?.takeIf { response.isSuccessful }?.toDomain() ?: throw CstvException(errors.from(response))
    }
    suspend fun delete(id: String) = unavailableOnIo {
        val response = api.deleteProfile(id)
        if (!response.isSuccessful) throw CstvException(errors.from(response))
    }
    private suspend fun <T> unavailableOnIo(block: suspend () -> T): T = try {
        block()
    } catch (_: java.io.IOException) {
        throw CstvException(com.cstv.app.domain.model.CstvError.Unavailable)
    }
    private fun com.cstv.app.data.remote.dto.CstvProfileDto.toDomain() = CstvProfile(id, name, avatarId, CstvTimestampParser.parseMillis(createdAt))
}
