package com.cstv.app.presentation.player.core

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Adapter mince (F39 §8.5, §9.3) reliant [PlaybackEngineController] (T23) à
 * l'interface [MediaVersionSwitchEngine] attendue par
 * [MediaVersionSwitchController] — non testable en JVM sans device
 * (AGENTS.md, comme `ExoPlaybackRecoveryEngine`). Contrairement à ce
 * dernier, ne reconstruit jamais le lecteur : [PlaybackEngineController.
 * setMediaItem] pose la cible sur l'instance existante, la bascule de
 * version ne changeant ni décodeur ni stratégie (§9.3, un seul contrôleur
 * de moteur actif).
 */
@UnstableApi
class ExoMediaVersionSwitchEngine(private val controller: PlaybackEngineController) : MediaVersionSwitchEngine {

    override fun currentPositionMs(): Long = controller.player.currentPosition
    override fun currentPlayWhenReady(): Boolean = controller.player.playWhenReady
    override fun currentSpeed(): Float = controller.player.playbackParameters.speed

    override fun prepareTarget(target: PlayableVersionTarget): Flow<MediaVersionSwitchEvent> = callbackFlow {
        val player = controller.player
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) trySend(MediaVersionSwitchEvent.Ready)
            }

            override fun onPlayerError(error: PlaybackException) {
                trySend(MediaVersionSwitchEvent.Failure)
            }
        }
        player.addListener(listener)

        // Ne touche ni position ni `playWhenReady` ici : le contrôleur les restaure lui-même une
        // fois `Ready` observé (§8.5 pt. 4) — poser la cible ne doit rien précipiter avant.
        controller.setMediaItem(MediaItem.fromUri(target.mediaUrl))
        player.playWhenReady = false
        player.prepare()

        awaitClose { player.removeListener(listener) }
    }

    override fun targetDurationMs(): Long? = controller.player.duration.takeIf { it != C.TIME_UNSET }

    override fun seekTo(positionMs: Long) {
        controller.player.seekTo(positionMs)
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        controller.player.playWhenReady = playWhenReady
    }

    override fun setSpeed(speed: Float) {
        controller.player.playbackParameters = controller.player.playbackParameters.withSpeed(speed)
    }
}
