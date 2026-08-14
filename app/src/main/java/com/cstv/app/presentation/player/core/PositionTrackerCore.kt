package com.cstv.app.presentation.player.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

/**
 * Valeurs exclusivement destinées à l'affichage de la jauge de lecture.
 *
 * La position réellement connue par le lecteur reste gérée par les écrans :
 * cette normalisation évite uniquement de présenter une reprise avec une durée
 * encore inconnue comme une jauge pleine.
 */
internal data class PlaybackProgressState(
    val positionMs: Long,
    val durationMs: Long
) {
    val isReady: Boolean
        get() = durationMs > 0L

    val sliderRangeEnd: Float
        get() = durationMs.toFloat().coerceAtLeast(1f)

    /**
     * Temps restant, affiché en décompte à droite de la jauge. Vaut 0 tant que
     * la durée est inconnue, `positionMs` étant alors normalisée à 0 ; le
     * `coerceAtLeast` couvre les positions que le lecteur reporte au-delà de la
     * durée en fin de flux.
     */
    val remainingMs: Long
        get() = (durationMs - positionMs).coerceAtLeast(0L)
}

/** Largeur minimale commune aux temps `MM:SS` et `H:MM:SS` du lecteur, préfixe `-` du décompte compris. */
internal val PLAYER_PROGRESS_TIME_LABEL_MIN_WIDTH: Dp = 64.dp

internal fun playbackProgressState(positionMs: Long, durationMs: Long): PlaybackProgressState {
    if (durationMs <= 0L) return PlaybackProgressState(positionMs = 0L, durationMs = 0L)

    return PlaybackProgressState(
        positionMs = positionMs.coerceIn(0L, durationMs),
        durationMs = durationMs
    )
}

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
