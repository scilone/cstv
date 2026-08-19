package com.cstv.app.data.repository

import com.cstv.app.data.remote.CstvErrorMapper
import com.cstv.app.data.remote.api.CstvApiService
import com.cstv.app.data.remote.dto.CstvProfileCreateDto
import com.cstv.app.domain.model.CstvProfile
import com.cstv.app.domain.model.CstvTimestampParser
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CstvProfileGateway @Inject constructor(private val api: CstvApiService, private val errors: CstvErrorMapper) {
    suspend fun create(name: String, avatarId: Int): CstvProfile = unavailableOnIo {
        val response = api.createProfile(CstvProfileCreateDto(name, avatarId))
        response.body()?.takeIf { response.isSuccessful }?.toDomain() ?: throw CstvException(errors.from(response))
    }

    /**
     * `maxAgeRatingProvided = false` (défaut) laisse le champ inchangé côté
     * backend. `true` avec `maxAgeRating = null` débride explicitement le
     * profil (F44 §8.1) : un champ simplement absent du JSON, avec Gson, ne
     * permet pas cette distinction — voir [CstvApiService.updateProfile].
     */
    suspend fun update(
        id: String,
        name: String? = null,
        avatarId: Int? = null,
        maxAgeRatingProvided: Boolean = false,
        maxAgeRating: Int? = null,
    ): CstvProfile = unavailableOnIo {
        val body = JsonObject().apply {
            name?.let { addProperty("name", it) }
            avatarId?.let { addProperty("avatarId", it) }
            if (maxAgeRatingProvided) {
                if (maxAgeRating != null) addProperty("maxAgeRating", maxAgeRating) else add("maxAgeRating", JsonNull.INSTANCE)
            }
        }
        val response = api.updateProfile(id, body)
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
    private fun com.cstv.app.data.remote.dto.CstvProfileDto.toDomain() =
        CstvProfile(id, name, avatarId, CstvTimestampParser.parseMillis(createdAt), maxAgeRating)
}
