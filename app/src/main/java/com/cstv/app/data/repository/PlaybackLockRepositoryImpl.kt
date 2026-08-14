package com.cstv.app.data.repository

import com.cstv.app.data.remote.api.CstvPlaybackLockApiService
import com.cstv.app.data.remote.dto.PlaybackLockConflictDto
import com.cstv.app.data.remote.dto.PlaybackLockDto
import com.cstv.app.data.remote.dto.PlaybackLockRequestDto
import com.cstv.app.domain.model.PlaybackLockHolder
import com.cstv.app.domain.model.PlaybackLockResult
import com.cstv.app.domain.repository.PlaybackLockRepository
import com.google.gson.Gson
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
class PlaybackLockRepositoryImpl @Inject constructor(
    private val api: CstvPlaybackLockApiService,
    private val gson: Gson,
) : PlaybackLockRepository {
    override suspend fun acquire(deviceId: String, deviceName: String?, takeover: Boolean): PlaybackLockResult = network {
        responseToResult(api.acquire(PlaybackLockRequestDto(deviceId, deviceName, takeover)), heldCode = "PLAYBACK_LOCK_HELD")
    }

    override suspend fun heartbeat(token: String): PlaybackLockResult = network {
        responseToResult(api.heartbeat("\"$token\""), heldCode = "PLAYBACK_LOCK_REVOKED")
    }

    override suspend fun release(token: String) {
        try { api.release("\"$token\"") } catch (error: Exception) { if (error is CancellationException) throw error }
    }

    private suspend fun network(block: suspend () -> PlaybackLockResult): PlaybackLockResult = try {
        block()
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        PlaybackLockResult.Unavailable
    }

    private fun responseToResult(response: retrofit2.Response<PlaybackLockDto>, heldCode: String): PlaybackLockResult {
        if (response.isSuccessful) return response.body()?.toResult() ?: PlaybackLockResult.Unavailable
        // F37 is deliberately fail-open: a revoked/expired CSTV session (401),
        // a network error and a server outage all remain non-blocking for Xtream
        // playback. Only the two explicit 409 lock contracts may block it.
        if (response.code() in 500..599) return PlaybackLockResult.Unavailable
        val conflict = runCatching { gson.fromJson(response.errorBody()?.string(), PlaybackLockConflictDto::class.java) }.getOrNull()
        val holder = conflict?.holder?.let { PlaybackLockHolder(it.deviceName?.takeIf(String::isNotBlank), it.heldForSeconds ?: 0L) }
        return when (conflict?.code) {
            heldCode -> if (heldCode == "PLAYBACK_LOCK_HELD") PlaybackLockResult.Held(holder ?: PlaybackLockHolder(null, 0L)) else PlaybackLockResult.Revoked(holder)
            else -> PlaybackLockResult.Unavailable
        }
    }

    private fun PlaybackLockDto.toResult() = PlaybackLockResult.Acquired(lockToken, heartbeatSeconds, ttlSeconds)
}
