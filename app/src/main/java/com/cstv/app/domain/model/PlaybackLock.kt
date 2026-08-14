package com.cstv.app.domain.model

data class PlaybackLockHolder(val deviceName: String?, val heldForSeconds: Long)

sealed interface PlaybackLockResult {
    data class Acquired(val token: String, val heartbeatSeconds: Long, val ttlSeconds: Long) : PlaybackLockResult
    data class Held(val holder: PlaybackLockHolder) : PlaybackLockResult
    data class Revoked(val holder: PlaybackLockHolder?) : PlaybackLockResult
    data object Unavailable : PlaybackLockResult
}

sealed interface PlaybackLockState {
    data object Idle : PlaybackLockState
    data class Held(val token: String, val heartbeatSeconds: Long, val ttlSeconds: Long) : PlaybackLockState
    data object FailOpen : PlaybackLockState
    data class Revoked(val holder: PlaybackLockHolder?) : PlaybackLockState
}

/** Screen-facing state. It is owned by the player ViewModels, never by a Composable. */
data class PlaybackLockUiState(
    val canStartPlayback: Boolean = false,
    val conflict: PlaybackLockHolder? = null,
    val takenOverBy: PlaybackLockHolder? = null,
    val failOpen: Boolean = false,
)
