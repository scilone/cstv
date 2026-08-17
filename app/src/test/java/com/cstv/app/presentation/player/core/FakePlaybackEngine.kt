package com.cstv.app.presentation.player.core

import com.cstv.app.domain.model.PlaybackRepairPlan
import com.cstv.app.domain.model.TrackFingerprint
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * Moteur déterministe pour les tests de [PlaybackRecoveryCoordinator] (T23 §8.8) — aucun device
 * ni ExoPlayer réel. Chaque appel à [applyPlan] consomme le script suivant dans [scripts], dans
 * l'ordre ; un appel au-delà du nombre de scripts fournis échoue bruyamment plutôt que de
 * silencieusement attendre le timeout, pour garder les tests lisibles.
 */
class FakePlaybackEngine(
    private val scripts: List<Flow<PlaybackEngineEvent>>,
    private val alternateAudioTrack: TrackFingerprint? = null,
) : PlaybackRecoveryEngine {
    val appliedPlans = mutableListOf<PlaybackRepairPlan>()
    val restoreStates = mutableListOf<PlaybackRestoreState>()
    val alternateAudioTrackExclusions = mutableListOf<TrackFingerprint?>()
    private var callIndex = 0

    override fun applyPlan(plan: PlaybackRepairPlan, restore: PlaybackRestoreState): Flow<PlaybackEngineEvent> {
        appliedPlans.add(plan)
        restoreStates.add(restore)
        val script = scripts.getOrNull(callIndex)
            ?: error("FakePlaybackEngine : aucun script pour l'appel #$callIndex (plan=$plan) — scripts fournis: ${scripts.size}")
        callIndex++
        return script
    }

    override suspend fun firstAlternateAudioTrack(excluding: TrackFingerprint?): TrackFingerprint? {
        alternateAudioTrackExclusions.add(excluding)
        return alternateAudioTrack
    }

    companion object {
        fun readyImmediately(): Flow<PlaybackEngineEvent> = flowOf(PlaybackEngineEvent.Ready)

        fun rendererFailureImmediately(failedTrack: TrackFingerprint? = null): Flow<PlaybackEngineEvent> =
            flowOf(PlaybackEngineEvent.RendererFailure(failedTrack))

        /** R9 : type par défaut NETWORK_SOURCE — un des trois types routés vers `RecoveryOutcome.Aborted`. */
        fun nonDecoderFailureImmediately(type: PlaybackFailureType = PlaybackFailureType.NETWORK_SOURCE): Flow<PlaybackEngineEvent> =
            flowOf(PlaybackEngineEvent.NonDecoderFailure(type))

        /** `Ready` puis un échec avant la fin de la fenêtre de stabilité (3 s) : l'essai échoue. */
        fun readyThenFailsBeforeStable(afterMs: Long = 1_000L, failedTrack: TrackFingerprint? = null): Flow<PlaybackEngineEvent> = flow {
            emit(PlaybackEngineEvent.Ready)
            delay(afterMs)
            emit(PlaybackEngineEvent.RendererFailure(failedTrack))
        }

        /** N'émet jamais rien et ne se termine jamais : simule un essai qui pend jusqu'au timeout. */
        fun neverResponds(): Flow<PlaybackEngineEvent> = flow { awaitCancellation() }
    }
}
