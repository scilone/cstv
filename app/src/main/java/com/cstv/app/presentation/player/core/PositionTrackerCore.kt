package com.cstv.app.presentation.player.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

internal class PositionSaveCadence(private val saveIntervalMs: Long = 5_000L) {
    init {
        require(saveIntervalMs > 0L) { "saveIntervalMs must be positive" }
    }

    private var lastSaveTimeMs: Long? = null

    fun shouldSave(positionMs: Long, durationMs: Long, nowMs: Long): Boolean {
        if (positionMs <= 0L || durationMs <= 0L) return false
        val previousSaveTimeMs = lastSaveTimeMs
        if (previousSaveTimeMs == null) {
            lastSaveTimeMs = nowMs
            return false
        }
        if (nowMs - previousSaveTimeMs < saveIntervalMs) return false

        lastSaveTimeMs = nowMs
        return true
    }
}

@Composable
fun TrackPlayerPosition(
    exoPlayer: ExoPlayer,
    contentKey: Any,
    isPlaying: Boolean,
    onPositionChanged: (positionMs: Long, durationMs: Long) -> Unit,
    onPeriodicSave: (positionMs: Long, durationMs: Long) -> Unit,
    onTrackerDispose: (positionMs: Long, durationMs: Long) -> Unit,
    updateIntervalMs: Long = 1000L,
    saveIntervalMs: Long = 5_000L
) {
    val cadence = remember(contentKey, saveIntervalMs) {
        PositionSaveCadence(saveIntervalMs)
    }

    LaunchedEffect(exoPlayer, contentKey, isPlaying) {
        if (isPlaying) {
            while (true) {
                val pos = exoPlayer.currentPosition
                val dur = exoPlayer.duration.coerceAtLeast(0L)
                if (dur > 0) {
                    onPositionChanged(pos, dur)
                    if (cadence.shouldSave(pos, dur, System.nanoTime() / 1_000_000L)) {
                        onPeriodicSave(pos, dur)
                    }
                }
                delay(updateIntervalMs)
            }
        }
    }

    DisposableEffect(exoPlayer, contentKey) {
        onDispose {
            onTrackerDispose(exoPlayer.currentPosition, exoPlayer.duration.coerceAtLeast(0L))
        }
    }
}
