package com.cstv.app.presentation.player.core

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * Moteur déterministe pour les tests de [MediaVersionSwitchController] (F39 §8.8) — même patron
 * que `FakePlaybackEngine` (T23) : chaque appel à [prepareTarget] consomme le script suivant dans
 * [scripts], dans l'ordre ; un appel au-delà échoue bruyamment plutôt que d'attendre le timeout en
 * silence.
 */
class FakeMediaVersionSwitchEngine(
    private val scripts: List<Flow<MediaVersionSwitchEvent>>,
    initialPositionMs: Long = 0L,
    initialPlayWhenReady: Boolean = true,
    initialSpeed: Float = 1f,
    private val targetDurationMs: Long? = null
) : MediaVersionSwitchEngine {
    val preparedTargets = mutableListOf<PlayableVersionTarget>()
    val seeks = mutableListOf<Long>()
    val playWhenReadyChanges = mutableListOf<Boolean>()
    val speedChanges = mutableListOf<Float>()
    private var callIndex = 0

    private var positionMs = initialPositionMs
    private var playWhenReady = initialPlayWhenReady
    private var speed = initialSpeed

    override fun currentPositionMs(): Long = positionMs
    override fun currentPlayWhenReady(): Boolean = playWhenReady
    override fun currentSpeed(): Float = speed

    override fun prepareTarget(target: PlayableVersionTarget): Flow<MediaVersionSwitchEvent> {
        preparedTargets.add(target)
        val script = scripts.getOrNull(callIndex)
            ?: error("FakeMediaVersionSwitchEngine : aucun script pour l'appel #$callIndex (target=$target) — scripts fournis: ${scripts.size}")
        callIndex++
        return script
    }

    override fun targetDurationMs(): Long? = targetDurationMs

    override fun seekTo(positionMs: Long) {
        seeks.add(positionMs)
        this.positionMs = positionMs
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        playWhenReadyChanges.add(playWhenReady)
        this.playWhenReady = playWhenReady
    }

    override fun setSpeed(speed: Float) {
        speedChanges.add(speed)
        this.speed = speed
    }

    companion object {
        fun readyImmediately(): Flow<MediaVersionSwitchEvent> = flowOf(MediaVersionSwitchEvent.Ready)

        fun failureImmediately(): Flow<MediaVersionSwitchEvent> = flowOf(MediaVersionSwitchEvent.Failure)

        fun readyAfter(delayMs: Long): Flow<MediaVersionSwitchEvent> = flow {
            delay(delayMs)
            emit(MediaVersionSwitchEvent.Ready)
        }

        /** N'émet jamais rien et ne se termine jamais : simule une cible qui pend jusqu'au timeout. */
        fun neverResponds(): Flow<MediaVersionSwitchEvent> = flow { awaitCancellation() }
    }
}
