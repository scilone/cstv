package com.cstv.app.data.playback

import com.cstv.app.data.local.storage.DeviceIdentityProvider
import com.cstv.app.domain.model.PlaybackLockResult
import com.cstv.app.domain.model.PlaybackLockState
import com.cstv.app.domain.repository.PlaybackLockRepository
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

@Singleton
class PlaybackLockManager @Inject constructor(
    private val repository: PlaybackLockRepository,
    private val identity: DeviceIdentityProvider,
    @Named("applicationScope") private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<PlaybackLockState>(PlaybackLockState.Idle)
    val state: StateFlow<PlaybackLockState> = _state.asStateFlow()
    private var heartbeat: Job? = null
    private val mutex = Mutex()

    suspend fun acquire(takeover: Boolean = false): PlaybackLockResult = mutex.withLock {
        if (!takeover && _state.value is PlaybackLockState.Held) return@withLock heldResult()
        // A timeout cancels the Retrofit call. Reacquiring with this stable
        // device id also recovers a request that reached the server just before
        // cancellation, without leaving a 90-second orphaned lock.
        val result = try {
            withTimeout(3_000L) { repository.acquire(identity.deviceId(), identity.deviceName(), takeover) }
        } catch (_: TimeoutCancellationException) {
            PlaybackLockResult.Unavailable
        }
        when (result) {
            is PlaybackLockResult.Acquired -> hold(result)
            PlaybackLockResult.Unavailable -> _state.value = PlaybackLockState.FailOpen
            is PlaybackLockResult.Revoked -> _state.value = PlaybackLockState.Revoked(result.holder)
            is PlaybackLockResult.Held -> Unit
        }
        result
    }

    fun release() {
        val held = _state.value as? PlaybackLockState.Held ?: run { _state.value = PlaybackLockState.Idle; return }
        heartbeat?.cancel(); heartbeat = null
        _state.value = PlaybackLockState.Idle
        scope.launch { repository.release(held.token) }
    }

    /** Clears all in-memory ownership when the CSTV account changes or signs out. */
    fun reset() {
        heartbeat?.cancel(); heartbeat = null
        _state.value = PlaybackLockState.Idle
    }

    private fun hold(result: PlaybackLockResult.Acquired) {
        heartbeat?.cancel()
        _state.value = PlaybackLockState.Held(result.token, result.heartbeatSeconds, result.ttlSeconds)
        heartbeat = scope.launch {
            while (true) {
                delay(result.heartbeatSeconds.coerceAtLeast(1) * 1_000L)
                when (val heartbeatResult = repository.heartbeat(result.token)) {
                    is PlaybackLockResult.Revoked -> { _state.value = PlaybackLockState.Revoked(heartbeatResult.holder); return@launch }
                    else -> Unit
                }
            }
        }
    }

    private fun heldResult(): PlaybackLockResult.Acquired {
        val held = _state.value as PlaybackLockState.Held
        return PlaybackLockResult.Acquired(held.token, held.heartbeatSeconds, held.ttlSeconds)
    }
}
