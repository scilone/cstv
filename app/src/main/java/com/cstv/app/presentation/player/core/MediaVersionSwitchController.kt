package com.cstv.app.presentation.player.core

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

/**
 * Cible de bascule (F39 §8.5) : juste assez pour poser un `MediaItem`. La
 * sélection de piste audio/sous-titres suit son cycle habituel une fois la
 * cible prête (préférence mémorisée du média, `onTracksChanged` côté écran)
 * — F39 ne la court-circuite pas.
 *
 * @param cacheKey correction F39-R3 : identifiant de cache hors-ligne
 *   (`DownloadedItem.movieContentId`/`episodeContentId`) de **cette** cible
 *   précise, posé sur le `MediaItem` par l'engine (§8.6, `useOfflineCache`)
 *   — sans lui, une bascule de version perdait le rattachement au
 *   téléchargement local éventuel de la cible, contrairement au premier
 *   chargement de l'écran qui, lui, le posait toujours.
 */
data class PlayableVersionTarget(val mediaUrl: String, val cacheKey: String? = null)

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
 *
 * Correction F39-R1 : le flux retourné par [prepareTarget] reste ouvert
 * (aucun `firstOrNull` côté implémentation) tant que l'appelant ne cesse pas
 * de le collecter — [MediaVersionSwitchController] l'observe désormais en
 * continu du premier `Ready` jusqu'à la fin de la fenêtre de stabilité, pour
 * ne manquer aucun `Failure` survenant entre les deux.
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

    /** Échec (timeout ou instabilité) : source précédente restaurée ET confirmée `Ready` à
     * nouveau, préférence jamais modifiée. */
    data object RolledBack : MediaVersionSwitchResult()

    /**
     * Correction F39-R1 : échec en cascade — la cible a échoué **et** la source précédente
     * elle-même n'a pas redonné `Ready` pendant le rollback. La position/lecture ont quand même
     * été restaurées au mieux (§7.5 : jamais d'écran noir volontaire), mais l'appelant ne doit pas
     * traiter ce cas comme un rollback pleinement réussi — ni, a fortiori, comme un succès.
     */
    data object RollbackFailed : MediaVersionSwitchResult()
}

/**
 * F39 §8.5 : bascule transactionnelle entre deux versions d'une même œuvre,
 * au-dessus d'un [MediaVersionSwitchEngine]. Le sélecteur (tâche 5) doit
 * rester fermé pendant le chargement et sérialiser ses appels ; la
 * génération interne n'est qu'un filet de sécurité si un second appel
 * survient malgré tout avant que le premier ne se termine.
 *
 * Correction F39-R1 (étape 7) : l'implémentation d'origine cessait de
 * collecter le flux de l'engine dès le premier `Ready`/`Failure` — un échec
 * survenant après `Ready` mais avant la fin des trois secondes de stabilité
 * n'était donc plus visible, et un `Failure` isolé avant `Ready` faisait
 * échouer la bascule immédiatement au lieu de laisser le budget global de 8 s
 * s'écouler (une réparation T23 côté écran peut produire un `Ready` tardif —
 * voir la coordination côté écran : `isSwitchingVersion` suspend l'appel à
 * `PlaybackRecoverySession.handleError` pendant toute la durée de
 * [switchTo], §9.3, pour qu'un seul mécanisme ne pilote le moteur à la
 * fois). Le contrôleur observe maintenant un unique flux en continu, du
 * début de [switchTo] jusqu'à sa conclusion : un `Failure` avant `Ready`
 * n'interrompt plus l'attente (seul le timeout de 8 s le fait), et un
 * `Failure` pendant la fenêtre de stabilité fait désormais échouer la
 * bascule au lieu d'être silencieusement ignoré.
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
    ): MediaVersionSwitchResult = coroutineScope {
        val myGeneration = generation.incrementAndGet()

        // 1. capture l'instantané avant toute modification.
        val positionMs = engine.currentPositionMs()
        val playWhenReady = engine.currentPlayWhenReady()
        val speed = engine.currentSpeed()

        // F39-R1 : un seul abonnement au flux de la cible pour toute la durée de la bascule
        // (attente de Ready + fenêtre de stabilité), relayé vers un Channel pour pouvoir le
        // consommer en deux temps sans jamais le réabonner (un second `prepareTarget` reposerait
        // le `MediaItem`).
        val events = Channel<MediaVersionSwitchEvent>(Channel.UNLIMITED)
        val collectJob = launch {
            engine.prepareTarget(target).collect { events.trySend(it) }
        }

        try {
            // 2-3. attend Ready sous le budget global de 8 s. Un `Failure` isolé n'interrompt plus
            // l'attente : T23 peut retenter côté écran (celui-ci suspend son propre déclenchement
            // de réparation tant que `isSwitchingVersion` est vrai, pour ne jamais avoir deux
            // pilotes du moteur) et produire un `Ready` tardif, toujours sous ce même budget.
            val readyObserved = withTimeoutOrNull(readyTimeoutMs) {
                var ready = false
                for (event in events) {
                    if (generation.get() != myGeneration) break
                    if (event is MediaVersionSwitchEvent.Ready) {
                        ready = true
                        break
                    }
                }
                ready
            }

            if (generation.get() != myGeneration) return@coroutineScope MediaVersionSwitchResult.Superseded

            if (readyObserved != true) {
                return@coroutineScope if (rollback(previous, positionMs, playWhenReady, speed)) {
                    MediaVersionSwitchResult.RolledBack
                } else {
                    MediaVersionSwitchResult.RollbackFailed
                }
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

            // 5. fenêtre de stabilité : continue d'observer le MÊME flux (toujours la même
            // souscription) pour détecter un échec survenant après Ready mais avant validation —
            // c'est précisément ce que l'implémentation d'origine perdait de vue (F39-R1).
            val failedDuringStability = withTimeoutOrNull(stabilityDelayMs) {
                var failed = false
                for (event in events) {
                    if (generation.get() != myGeneration) break
                    if (event is MediaVersionSwitchEvent.Failure) {
                        failed = true
                        break
                    }
                }
                failed
            } ?: false // timeout écoulé sans échec observé => fenêtre stable.

            if (generation.get() != myGeneration) return@coroutineScope MediaVersionSwitchResult.Superseded

            if (failedDuringStability) {
                return@coroutineScope if (rollback(previous, positionMs, playWhenReady, speed)) {
                    MediaVersionSwitchResult.RolledBack
                } else {
                    MediaVersionSwitchResult.RollbackFailed
                }
            }

            onCommitSeriesPreference?.invoke()
            MediaVersionSwitchResult.Switched
        } finally {
            collectJob.cancel()
            events.close()
        }
    }

    /**
     * Reconstruit la source précédente et restaure sa position — au mieux : la restauration a
     * toujours lieu (§7.5, jamais d'écran noir), même si [previous] elle-même n'atteint pas
     * `Ready` (F39-R1 : c'est justement ce que le [Boolean] retourné permet à l'appelant de
     * distinguer, plutôt que d'annoncer systématiquement un rollback réussi).
     *
     * @return `true` si [previous] a bien redonné `Ready`, `false` sinon (timeout ou `Failure`).
     */
    private suspend fun rollback(previous: PlayableVersionTarget, positionMs: Long, playWhenReady: Boolean, speed: Float): Boolean {
        val previousReady = withTimeoutOrNull(readyTimeoutMs) {
            engine.prepareTarget(previous).firstOrNull {
                it is MediaVersionSwitchEvent.Ready || it is MediaVersionSwitchEvent.Failure
            }
        } is MediaVersionSwitchEvent.Ready
        engine.seekTo(positionMs)
        engine.setPlayWhenReady(playWhenReady)
        engine.setSpeed(speed)
        return previousReady
    }

    companion object {
        const val READY_TIMEOUT_MS = 8_000L
        const val STABILITY_DELAY_MS = 3_000L
        const val END_OF_TARGET_MARGIN_MS = 2_000L
    }
}
