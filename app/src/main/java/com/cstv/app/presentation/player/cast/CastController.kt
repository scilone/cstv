package com.cstv.app.presentation.player.cast

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.gms.cast.framework.CastContext

/**
 * État exposé au lecteur : quel [Player] piloter (ExoPlayer local ou CastPlayer),
 * si un cast est en cours et le nom de l'appareil.
 */
data class CastControllerState(
    val activePlayer: Player,
    val isCasting: Boolean,
    val deviceName: String?
)

/**
 * Gère la bascule lecture locale ↔ Chromecast pour un [exoPlayer] donné.
 *
 * - Ne fait rien si le Cast est indisponible ([available] = false, cf.
 *   CastAvailabilityProvider) : `activePlayer` reste l'ExoPlayer.
 * - À l'ouverture d'une session Cast : charge le [currentMediaItem] courant sur
 *   l'appareil à la position locale et met la lecture locale en pause (handoff
 *   local → cast).
 * - À la fermeture : replace la lecture locale à la position castée et reprend
 *   (handoff cast → local).
 *
 * Le CastPlayer et le listener sont libérés automatiquement (DisposableEffect).
 */
@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun rememberCastController(
    exoPlayer: ExoPlayer,
    available: Boolean,
    currentMediaItem: MediaItem?
): State<CastControllerState> {
    val context = LocalContext.current

    val castPlayer = remember(available) {
        if (!available) return@remember null
        try {
            val castContext = CastContext.getSharedInstance(context)
            CastPlayer(castContext)
        } catch (e: Exception) {
            null
        }
    }

    val state = remember { mutableStateOf(CastControllerState(exoPlayer, isCasting = false, deviceName = null)) }

    // Toujours refléter le dernier item courant : si l'utilisateur zappe/relance
    // pendant un cast, on met à jour l'appareil.
    DisposableEffect(castPlayer, currentMediaItem) {
        val cp = castPlayer ?: return@DisposableEffect onDispose {}
        if (state.value.isCasting && currentMediaItem != null) {
            cp.setMediaItem(currentMediaItem, exoPlayer.currentPosition)
            cp.playWhenReady = true
            cp.prepare()
        }
        onDispose {}
    }

    DisposableEffect(castPlayer) {
        val cp = castPlayer ?: return@DisposableEffect onDispose {}

        fun deviceName(): String? = try {
            CastContext.getSharedInstance(context)
                .sessionManager.currentCastSession?.castDevice?.friendlyName
        } catch (e: Exception) {
            null
        }

        val listener = object : SessionAvailabilityListener {
            override fun onCastSessionAvailable() {
                val position = exoPlayer.currentPosition
                val wasPlaying = exoPlayer.playWhenReady
                exoPlayer.playWhenReady = false
                currentMediaItem?.let {
                    cp.setMediaItem(it, position)
                    cp.playWhenReady = wasPlaying
                    cp.prepare()
                }
                state.value = CastControllerState(cp, isCasting = true, deviceName = deviceName())
            }

            override fun onCastSessionUnavailable() {
                val position = cp.currentPosition
                if (position > 0) exoPlayer.seekTo(position)
                exoPlayer.playWhenReady = true
                state.value = CastControllerState(exoPlayer, isCasting = false, deviceName = null)
            }
        }
        cp.setSessionAvailabilityListener(listener)

        onDispose {
            cp.setSessionAvailabilityListener(null)
            cp.release()
        }
    }

    return state
}
