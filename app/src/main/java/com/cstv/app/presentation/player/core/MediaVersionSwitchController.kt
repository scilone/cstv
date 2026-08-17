package com.cstv.app.presentation.player.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

/**
 * Cible de bascule (F39 §8.5) : juste assez pour poser un `MediaItem`. La
 * sélection de piste audio/sous-titres suit son cycle habituel une fois la
 * cible prête (préférence mémorisée du média, `onTracksChanged` côté écran)
 * — F39 ne la court-circuite pas.
 */
data class PlayableVersionTarget(val mediaUrl: String)

/** Événement remonté pendant la préparation d'une cible (§8.5 pt. 3). */
sealed class MediaVersionSwitchEvent {
    data object Ready : MediaVersionSwitchEvent()
    data object Failure : MediaVersionSwitchEvent()
}

/**
 * Moteur piloté par [MediaVersionSwitchController] — implémenté en
 * production par un adapter mince au-dessus de `PlaybackEngineController`
 * (T23, câblage réel tâche 5), remplacé par un moteur déterministe en test
 * (AGENTS.md : aucun device requis). Contrairement à `PlaybackRecoveryEngine.
 * applyPlan` (T23, qui reconstruit tout le lecteur pour changer de
 * décodeur), la bascule de version pose un nouveau `MediaItem` sur le
 * lecteur **existant** : la version ne change ni décodeur ni stratégie, et
 * §9.3 interdit un second contrôleur de moteur concurrent.
 */
interface MediaVersionSwitchEngine {
    fun currentPositionMs(): Long
    fun currentPlayWhenReady(): Boolean
    fun currentSpeed(): Float

    /** Pose [target], puis émet ses événements jusqu'à ce que l'appelant cesse de collecter. */
    fun prepareTarget(target: PlayableVersionTarget): Flow<MediaVersionSwitchEvent>

    /** Durée réelle de la cible une fois prête, `null` si encore inconnue. */
    fun targetDurationMs(): Long?

    fun seekTo(positionMs: Long)
    fun setPlayWhenReady(playWhenReady: Boolean)
    fun setSpeed(speed: Float)
}

/** Issue de [MediaVersionSwitchController.switchTo] (§8.5, §7.5). */
sealed class MediaVersionSwitchResult {
    data object Switched : MediaVersionSwitchResult()

    /** Une génération plus récente a été demandée pendant l'attente — cette bascule n'a jamais
     *  touché la position/lecture après coup (§8.5 : « une génération de switch empêche une
     *  réponse tardive de la cible A d'écraser une cible B choisie ensuite »). */
    data object Superseded : MediaVersionSwitchResult()

    /** Échec (timeout ou instabilité) : source précédente restaurée, préférence jamais modifiée. */
    data object RolledBack : MediaVersionSwitchResult()
}

/**
 * F39 §8.5 : bascule transactionnelle entre deux versions d'une même œuvre,
 * au-dessus d'un [MediaVersionSwitchEngine]. Le sélecteur (tâche 5) doit
 * rester fermé pendant le chargement et sérialiser ses appels ; la
 * génération interne n'est qu'un filet de sécurité si un second appel
 * survient malgré tout avant que le premier ne se termine.
 */
class MediaVersionSwitchController(
    private val engine: MediaVersionSwitchEngine,
    private val readyTimeoutMs: Long = READY_TIMEOUT_MS,
    private val stabilityDelayMs: Long = STABILITY_DELAY_MS
) {
    private val generation = AtomicInteger(0)

    /**
     * @param previous la source actuellement lue, pour le rollback (§8.5 pt. 6) — jamais
     *   reconstruite depuis [target], au cas où l'appelant l'aurait déjà modifiée entre-temps.
     * @param onCommitSeriesPreference appelé seulement en cas de succès pour une série (§8.5 pt. 5,
     *   §7.3) — jamais sur rollback ni sur superseded : « la préférence n'est jamais modifiée ».
     */
    suspend fun switchTo(
        previous: PlayableVersionTarget,
        target: PlayableVersionTarget,
        onCommitSeriesPreference: (suspend () -> Unit)? = null
    ): MediaVersionSwitchResult {
        val myGeneration = generation.incrementAndGet()

        // 1. capture l'instantané avant toute modification.
        val positionMs = engine.currentPositionMs()
        val playWhenReady = engine.currentPlayWhenReady()
        val speed = engine.currentSpeed()

        // 2-3. prépare la cible sans effacer l'instantané ; attend Ready/Failure sous 8 s — T23
        //      peut exécuter sa propre réparation dans ce délai avant de conclure à l'échec.
        val readyEvent = withTimeoutOrNull(readyTimeoutMs) {
            engine.prepareTarget(target).firstOrNull {
                it is MediaVersionSwitchEvent.Ready || it is MediaVersionSwitchEvent.Failure
            }
        }

        if (generation.get() != myGeneration) return MediaVersionSwitchResult.Superseded

        if (readyEvent !is MediaVersionSwitchEvent.Ready) {
            rollback(previous, positionMs, playWhenReady, speed)
            return MediaVersionSwitchResult.RolledBack
        }

        // 4. seek borné : position source, ou près de la fin si la cible est plus courte.
        val targetDurationMs = engine.targetDurationMs()
        val seekPositionMs = if (targetDurationMs != null && targetDurationMs > 0) {
            minOf(positionMs, maxOf(0L, targetDurationMs - END_OF_TARGET_MARGIN_MS))
        } else {
            positionMs
        }
        engine.seekTo(seekPositionMs)
        engine.setPlayWhenReady(playWhenReady)
        engine.setSpeed(speed)

        // 5. après trois secondes stables, valide la cible.
        delay(stabilityDelayMs)

        if (generation.get() != myGeneration) return MediaVersionSwitchResult.Superseded

        onCommitSeriesPreference?.invoke()
        return MediaVersionSwitchResult.Switched
    }

    private suspend fun rollback(previous: PlayableVersionTarget, positionMs: Long, playWhenReady: Boolean, speed: Float) {
        // 6. reconstruit la source précédente et restaure sa position — au mieux : un rollback qui
        //    échouerait à son tour ne doit jamais bloquer indéfiniment l'appelant.
        withTimeoutOrNull(readyTimeoutMs) {
            engine.prepareTarget(previous).firstOrNull {
                it is MediaVersionSwitchEvent.Ready || it is MediaVersionSwitchEvent.Failure
            }
        }
        engine.seekTo(positionMs)
        engine.setPlayWhenReady(playWhenReady)
        engine.setSpeed(speed)
    }

    companion object {
        const val READY_TIMEOUT_MS = 8_000L
        const val STABILITY_DELAY_MS = 3_000L
        const val END_OF_TARGET_MARGIN_MS = 2_000L
    }
}
