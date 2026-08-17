package com.cstv.app.presentation.player.core

import com.cstv.app.di.IptvLog
import com.cstv.app.domain.model.DecoderStrategy
import com.cstv.app.domain.model.PlaybackRepairPlan
import com.cstv.app.domain.model.TrackFingerprint
import com.cstv.app.domain.model.TrackKind
import com.cstv.app.domain.repository.PlaybackRepairRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Position/vitesse à restaurer à chaque essai de la séquence (T23 §8.4). */
data class PlaybackRestoreState(
    val positionMs: Long,
    val playWhenReady: Boolean,
    val speed: Float
)

/**
 * Événement brut remonté par le moteur de lecture pendant un essai (§8.8 : `FakePlaybackEngine`
 * en émet de façon déterministe en test). [NonDecoderFailure] interrompt l'essai en cours
 * immédiatement (§8.3 : un problème réseau/live-window ne se répare pas en changeant de décodeur,
 * épuiser les trois essais dessus masquerait inutilement une panne réseau) ; `RendererFailure` fait
 * passer à l'étape suivante de la séquence de réparation.
 *
 * Review R9 : avant, tout événement non-`RendererFailure` devenait un unique `SourceFailure`
 * traité comme un échec « appareil » — une coupure réseau ou un incident `LIVE_WINDOW` survenu
 * *pendant* un essai de réparation s'affichait alors comme une incompatibilité matérielle.
 * [NonDecoderFailure.type] porte la vraie catégorie ([PlaybackFailureType.NETWORK_SOURCE],
 * [PlaybackFailureType.LIVE_WINDOW] ou [PlaybackFailureType.UNKNOWN]) pour que l'appelant la route
 * vers son comportement existant plutôt que vers le message « illisible sur cet appareil ».
 */
sealed class PlaybackEngineEvent {
    data object Ready : PlaybackEngineEvent()
    data class RendererFailure(val failedTrack: TrackFingerprint?) : PlaybackEngineEvent()
    data class NonDecoderFailure(val type: PlaybackFailureType) : PlaybackEngineEvent()
}

/**
 * Moteur de lecture piloté par le coordinateur — implémenté en production par un adapter mince
 * au-dessus de [PlaybackEngineController] (câblage réel en tâche 7), remplacé par
 * `FakePlaybackEngine` en test (§8.8, AGENTS.md : aucun device requis).
 */
interface PlaybackRecoveryEngine {
    /**
     * Reconstruit le lecteur selon [plan], restaure [restore], puis émet les événements de cet
     * essai jusqu'à ce que l'appelant cesse de collecter (nouvel essai ou séquence terminée).
     */
    fun applyPlan(plan: PlaybackRepairPlan, restore: PlaybackRestoreState): Flow<PlaybackEngineEvent>

    /**
     * Première piste audio alternative disponible pour l'étape 3 (§7.3), déjà ordonnée par
     * préférence de langue puis support codec par l'implémentation réelle. `null` si aucune autre
     * piste audio n'existe — l'étape 3 est alors sautée (cas limite §7.4).
     */
    suspend fun firstAlternateAudioTrack(excluding: TrackFingerprint?): TrackFingerprint?
}

/**
 * Review R9 : trois issues distinctes plutôt qu'un unique `FinalFailure` — seul
 * [DecoderExhausted] (les trois essais de décodage ont réellement échoué/timeout) doit persister
 * l'échec, notifier [PlaybackRecoveryExhaustionListener] et afficher le message « illisible sur cet
 * appareil ». [Aborted] (réseau/live-window/inconnu survenu *pendant* un essai) doit être routé par
 * l'appelant vers le comportement d'erreur existant pour ce type, pas vers un faux diagnostic
 * matériel.
 */
sealed class RecoveryOutcome {
    data class Stable(val winningPlan: PlaybackRepairPlan) : RecoveryOutcome()
    data object DecoderExhausted : RecoveryOutcome()
    data class Aborted(val type: PlaybackFailureType) : RecoveryOutcome()
}

/**
 * Point d'extension tâche 6 (§8.6, §10 tâche 6) : notifié exactement une fois quand la séquence
 * s'épuise ([RecoveryOutcome.DecoderExhausted]), jamais avant (jamais sur [RecoveryOutcome.Aborted],
 * review R9). **F40** le consultera pour passer à la
 * variante suivante sur une chaîne en direct ; **F39** s'appuie sur ce même épuisement pour son
 * rollback (jamais au premier renderer error). Aucune implémentation F39/F40 ici — seule
 * l'interface est posée, sans consommateur réel avant leurs tickets respectifs. Un appelant
 * VOD/série ne passe simplement aucun listener : aucune régression possible sur ces écrans, qui
 * n'ont pas de consommateur F40 (chaînes uniquement).
 */
fun interface PlaybackRecoveryExhaustionListener {
    fun onSequenceExhausted(kind: String, providerId: Int)
}

/**
 * Point d'extension tâche 6 (§8.4, §10 tâche 6) : fournisseur d'une position de tampon pour la
 * restauration en direct, que **F41** branchera. `null` (fournisseur absent, ou fournisseur
 * présent sans valeur) laisse le direct reprendre au live edge — comportement déjà couvert par la
 * tâche 5, sans dépendance dure sur F41 qui n'existe pas encore à ce stade de la livraison.
 */
fun interface LiveBufferPositionProvider {
    fun bufferedPositionMs(kind: String, providerId: Int): Long?
}

private sealed class AttemptOutcome {
    data object Success : AttemptOutcome()
    data object RendererFailed : AttemptOutcome()
    data class Aborted(val type: PlaybackFailureType) : AttemptOutcome()
    data object TimedOut : AttemptOutcome()
}

/** Résultat de [PlaybackRecoveryCoordinator.runSequence] (review R9) — distingue un épuisement
 * réel de décodage d'une interruption par un événement non lié au décodage. */
private sealed class SequenceResult {
    data class Success(val plan: PlaybackRepairPlan) : SequenceResult()
    data class Aborted(val type: PlaybackFailureType) : SequenceResult()
    data object Exhausted : SequenceResult()
}

/**
 * Machine d'états de réparation (T23 §8.4, §9.1) : `SOFTWARE_PREFERRED` → piste désactivée →
 * autre piste audio, chaque étape tentée seulement si la précédente échoue. Consulte/écrit le
 * profil mémorisé ([PlaybackRepairRepository], tâche 3) en début et fin de séquence.
 */
class PlaybackRecoveryCoordinator(
    private val engine: PlaybackRecoveryEngine,
    private val repository: PlaybackRepairRepository,
) {
    /**
     * Plan à essayer directement à l'ouverture d'un média (§8.4 : « un profil mémorisé est essayé
     * directement lors d'une nouvelle lecture ») — [PlaybackRepairPlan.DEFAULT] si aucun n'est
     * mémorisé pour ce (appareil, média). N'implique aucun essai : c'est à l'appelant d'ouvrir le
     * lecteur avec ce plan et de signaler un échec via [recoverFromDecodingFailure].
     */
    suspend fun initialPlan(kind: String, providerId: Int): PlaybackRepairPlan =
        repository.getRepairPlan(kind, providerId) ?: PlaybackRepairPlan.DEFAULT

    /**
     * Lance la séquence de réparation après un échec de décodage. Si l'essai qui vient d'échouer
     * utilisait un profil mémorisé ([wasUsingMemorizedPlan]), ce profil est d'abord supprimé
     * (§7.4, §8.4 : « il est supprimé puis la séquence repart depuis la stratégie par défaut »),
     * puis la séquence à trois étapes s'exécute dans l'ordre fixe, bornée par un timeout global
     * de [SEQUENCE_TIMEOUT_MS] en plus du timeout par essai ([ATTEMPT_TIMEOUT_MS]). Un succès
     * mémorise le plan gagnant ; un épuisement réel des trois essais de décodage retourne
     * [RecoveryOutcome.DecoderExhausted] sans écrire de profil (§7.2, §7.6 — jamais d'écran noir
     * silencieux : l'appelant affiche le message final avec « Réessayer »), et notifie
     * [exhaustionListener] exactement une fois si présent (point d'extension F39/F40, tâche 6 —
     * `null` pour VOD/série, qui n'ont pas de consommateur F40). Un événement non lié au décodage
     * pendant un essai (réseau, live-window, inconnu) retourne [RecoveryOutcome.Aborted] à la
     * place, **sans** notifier [exhaustionListener] ni persister d'échec (review R9) : c'est à
     * l'appelant de router ce cas vers son comportement d'erreur existant pour ce type.
     */
    suspend fun recoverFromDecodingFailure(
        kind: String,
        providerId: Int,
        wasUsingMemorizedPlan: Boolean,
        initialFailedTrack: TrackFingerprint?,
        restore: PlaybackRestoreState,
        exhaustionListener: PlaybackRecoveryExhaustionListener? = null
    ): RecoveryOutcome {
        if (wasUsingMemorizedPlan) repository.clearRepairPlan(kind, providerId)

        val sequenceStartedAtMs = System.currentTimeMillis()
        val result = withTimeoutOrNull(SEQUENCE_TIMEOUT_MS) {
            runSequence(initialFailedTrack, restore)
        } ?: SequenceResult.Exhausted
        val sequenceDurationMs = System.currentTimeMillis() - sequenceStartedAtMs

        val outcome = when (result) {
            is SequenceResult.Success -> {
                repository.saveRepairPlan(kind, providerId, result.plan)
                RecoveryOutcome.Stable(result.plan)
            }
            is SequenceResult.Aborted -> RecoveryOutcome.Aborted(result.type)
            SequenceResult.Exhausted -> {
                exhaustionListener?.onSequenceExhausted(kind, providerId)
                RecoveryOutcome.DecoderExhausted
            }
        }
        // §8.7 : uniquement type d'erreur, numéro d'étape, durée et résultat — jamais d'URL, de
        // piste ou de codec du catalogue.
        IptvLog.d(LOG_TAG, "séquence terminée résultat=${outcome.logLabel()} durée=${sequenceDurationMs}ms")
        return outcome
    }

    private suspend fun runSequence(initialFailedTrack: TrackFingerprint?, restore: PlaybackRestoreState): SequenceResult {
        // Étape 1 : décodeur logiciel préféré — R2 : ne porte que sur le type de renderer
        // identifié en erreur (`null` = piste non identifiée, séquence à l'aveugle §7.4 ; la
        // politique de rendu (PlayerDecoderPolicy) reste conservatrice dans ce cas).
        val step1 = PlaybackRepairPlan(decoderStrategy = DecoderStrategy.SOFTWARE_PREFERRED, softwarePreferredTrackKind = initialFailedTrack?.trackKind)
        when (val outcome1 = runAttempt(step1, restore, step = 1)) {
            AttemptOutcome.Success -> return SequenceResult.Success(step1)
            is AttemptOutcome.Aborted -> return SequenceResult.Aborted(outcome1.type)
            AttemptOutcome.RendererFailed, AttemptOutcome.TimedOut -> Unit
        }

        // Étape 2 : même stratégie, piste fautive désactivée — sautée si elle n'a pas pu être
        // identifiée (§7.4, hypothèse étape 1 : la séquence se déroule alors à l'aveugle).
        if (initialFailedTrack != null) {
            val step2 = step1.copy(disabledTrack = initialFailedTrack)
            when (val outcome2 = runAttempt(step2, restore, step = 2)) {
                AttemptOutcome.Success -> return SequenceResult.Success(step2)
                is AttemptOutcome.Aborted -> return SequenceResult.Aborted(outcome2.type)
                AttemptOutcome.RendererFailed, AttemptOutcome.TimedOut -> Unit
            }
        }

        // Étape 3 : piste fautive réactivée, autre piste audio sélectionnée si disponible.
        val alternate = engine.firstAlternateAudioTrack(excluding = initialFailedTrack) ?: return SequenceResult.Exhausted
        val step3 = PlaybackRepairPlan(decoderStrategy = DecoderStrategy.SOFTWARE_PREFERRED, softwarePreferredTrackKind = initialFailedTrack?.trackKind, preferredAudio = alternate)
        return when (val outcome3 = runAttempt(step3, restore, step = 3)) {
            AttemptOutcome.Success -> SequenceResult.Success(step3)
            is AttemptOutcome.Aborted -> SequenceResult.Aborted(outcome3.type)
            AttemptOutcome.RendererFailed, AttemptOutcome.TimedOut -> SequenceResult.Exhausted
        }
    }

    /**
     * Un essai réussit après `Ready` puis [STABILITY_WINDOW_MS] sans nouvel événement d'échec —
     * une [PlaybackEngineEvent.RendererFailure]/[PlaybackEngineEvent.NonDecoderFailure] pendant
     * cette fenêtre annule la stabilité en cours. Borné par [ATTEMPT_TIMEOUT_MS] au total pour cet
     * essai (§8.4). [step] (1 à 3) n'alimente que l'observabilité (§8.7, review R12) — aucune
     * incidence sur la logique.
     */
    private suspend fun runAttempt(plan: PlaybackRepairPlan, restore: PlaybackRestoreState, step: Int): AttemptOutcome = coroutineScope {
        val attemptStartedAtMs = System.currentTimeMillis()
        val outcome = CompletableDeferred<AttemptOutcome>()
        var stabilityJob: Job? = null

        val timeoutJob = launch {
            delay(ATTEMPT_TIMEOUT_MS)
            outcome.complete(AttemptOutcome.TimedOut)
        }
        val collectorJob = launch {
            engine.applyPlan(plan, restore).collect { event ->
                when (event) {
                    is PlaybackEngineEvent.Ready -> {
                        if (stabilityJob == null) {
                            stabilityJob = launch {
                                delay(STABILITY_WINDOW_MS)
                                outcome.complete(AttemptOutcome.Success)
                            }
                        }
                    }
                    is PlaybackEngineEvent.RendererFailure -> {
                        stabilityJob?.cancel()
                        stabilityJob = null
                        outcome.complete(AttemptOutcome.RendererFailed)
                    }
                    is PlaybackEngineEvent.NonDecoderFailure -> {
                        stabilityJob?.cancel()
                        stabilityJob = null
                        outcome.complete(AttemptOutcome.Aborted(event.type))
                    }
                }
            }
        }

        val result = outcome.await()
        timeoutJob.cancel()
        collectorJob.cancel()
        stabilityJob?.cancel()
        // §8.7, review R12 : type d'erreur, numéro d'étape, durée, résultat — jamais de piste/codec.
        IptvLog.d(LOG_TAG, "essai $step -> ${result.logLabel()} durée=${System.currentTimeMillis() - attemptStartedAtMs}ms")
        result
    }

    private fun AttemptOutcome.logLabel(): String = when (this) {
        AttemptOutcome.Success -> "succès"
        AttemptOutcome.RendererFailed -> "échec décodeur"
        is AttemptOutcome.Aborted -> "interrompu(${type.name.lowercase()})"
        AttemptOutcome.TimedOut -> "timeout"
    }

    private fun RecoveryOutcome.logLabel(): String = when (this) {
        is RecoveryOutcome.Stable -> "stable"
        RecoveryOutcome.DecoderExhausted -> "épuisé"
        is RecoveryOutcome.Aborted -> "interrompu(${type.name.lowercase()})"
    }

    companion object {
        private const val LOG_TAG = "T23-Repair"
        const val ATTEMPT_TIMEOUT_MS = 8_000L
        const val SEQUENCE_TIMEOUT_MS = 24_000L
        const val STABILITY_WINDOW_MS = 3_000L

        /** Marge sous laquelle on ne relance pas littéralement au dernier frame (§8.4). */
        private const val VOD_END_MARGIN_MS = 2_000L

        /**
         * Position restaurée pour un essai VOD/épisode/téléchargement (§8.4) :
         * `min(positionAvantErreur, duration-2s)`, plafonnée à 0 dans les deux sens (un média très
         * court ou une position négative ne doivent jamais produire une position hors bornes).
         */
        fun vodRestorePositionMs(positionBeforeErrorMs: Long, durationMs: Long): Long {
            val ceiling = (durationMs - VOD_END_MARGIN_MS).coerceAtLeast(0L)
            return positionBeforeErrorMs.coerceIn(0L, ceiling)
        }

        /**
         * Position de restauration en direct (§8.4, point d'extension F41 — tâche 6) : la
         * position de tampon fournie par [provider] si présente, sinon `null` pour indiquer au
         * lecteur de reprendre au live edge (comportement par défaut, déjà couvert tâche 5).
         */
        fun liveRestorePositionMs(kind: String, providerId: Int, provider: LiveBufferPositionProvider?): Long? =
            provider?.bufferedPositionMs(kind, providerId)
    }
}
