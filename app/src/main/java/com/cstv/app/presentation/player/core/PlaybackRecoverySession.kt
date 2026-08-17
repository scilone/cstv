package com.cstv.app.presentation.player.core

import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import com.cstv.app.domain.model.PlaybackRepairPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Résultat de [PlaybackRecoverySession.handleError] — ce que l'appelant doit faire ensuite (review
 * R6, R8) : la qualification de l'erreur et le déclenchement de la séquence T23 sortent du
 * Composable plutôt que d'y être décidés (§8.2/§9.2 : « Compose observe l'état »), et cette
 * qualification précède tout changement de source côté appelant (repli m3u8→ts sur Live, R8).
 */
sealed class PlaybackErrorHandling {
    /** Une réparation est déjà en cours pour la génération courante — l'écran ne doit rien faire,
     * le listener interne de l'engine pilote déjà cet essai. */
    data object AlreadyRepairing : PlaybackErrorHandling()

    /** Pas un échec de décodage (ou aucun coordinateur disponible) : l'appelant applique son
     * comportement existant pour ce [type] (repli réseau, live-window, message générique…). */
    data class NotDecoder(val type: PlaybackFailureType) : PlaybackErrorHandling()

    /** La séquence de réparation a démarré ; son issue sera livrée au `onOutcome` de
     * [PlaybackRecoverySession.handleError]. */
    data object RepairStarted : PlaybackErrorHandling()
}

/**
 * Possède le job/génération de réparation et la cible courante pour UN écran lecteur (T23-R3, R4,
 * R6, R7) — extrait des trois Composables lecteur qui dupliquaient cette logique chacun de leur
 * côté (R6). Un changement de cible ([forTarget]) ou une fermeture ([cancel]) annule tout job en
 * cours et incrémente la génération : les callbacks d'un essai obsolète sont rejetés avant toute
 * reconstruction, écriture en base ou mise à jour d'état (numéro de tentative monotonique exigé
 * par §8.6).
 */
@UnstableApi
class PlaybackRecoverySession(
    private val scope: CoroutineScope,
    private val engine: ExoPlaybackRecoveryEngine?,
    private val coordinator: PlaybackRecoveryCoordinator?,
) {
    private var generation = 0
    private var job: Job? = null
    private var currentKind: String? = null
    private var currentProviderId: Int? = null
    private var wasUsingMemorizedPlan = false

    /** true tant qu'un essai de réparation est en cours pour la génération courante. */
    val isRepairing: Boolean get() = job != null

    /**
     * (Ré)initialise la session pour [kind]/[providerId] : annule tout job/état résiduel de la
     * cible précédente (R3), puis reconstruit *explicitement* le plan mémorisé — ou
     * [PlaybackRepairPlan.DEFAULT] s'il n'y en a pas — plutôt que de laisser la stratégie ou les
     * overrides du média précédent contaminer celui-ci (R4). L'écran doit ensuite poser son
     * `MediaItem` et appeler `prepare()` sur `engineController.player` (potentiellement une
     * nouvelle instance après cet appel).
     */
    suspend fun forTarget(kind: String, providerId: Int) {
        cancel()
        currentKind = kind
        currentProviderId = providerId
        val plan = coordinator?.initialPlan(kind, providerId) ?: PlaybackRepairPlan.DEFAULT
        wasUsingMemorizedPlan = plan != PlaybackRepairPlan.DEFAULT
        engine?.prepareInitialPlan(plan)
    }

    /** À appeler depuis `onTracksChanged` de l'écran : applique la piste du profil mémorisé dès
     * qu'elle est identifiable dans les pistes du nouveau média (R1). No-op sans ajustement en
     * attente (aucun profil, ou profil sans `disabledTrack`/`preferredAudio`). */
    fun applyPendingTrackSelectionOfInitialPlan() {
        engine?.applyPendingInitialTrackSelection()
    }

    /** Annule tout job/état en cours — fermeture de l'écran ou changement de cible (R3). */
    fun cancel() {
        generation++
        job?.cancel()
        job = null
    }

    /**
     * Qualifie [error] et démarre la séquence de réparation si c'est un échec de décodage (§7.2,
     * §7.4). [onOutcome] est appelé avec le résultat final une fois la séquence terminée, sauf si
     * la cible a changé entre-temps (callback d'une génération obsolète silencieusement rejeté,
     * R3). Retourne [PlaybackErrorHandling.NotDecoder] sans rien déclencher pour tout ce qui n'est
     * pas un échec de décodage — c'est à l'appelant d'agir en conséquence (R8 : notamment *avant*
     * un éventuel repli de source).
     */
    fun handleError(
        error: PlaybackException,
        restore: PlaybackRestoreState,
        exhaustionListener: PlaybackRecoveryExhaustionListener? = null,
        onOutcome: (RecoveryOutcome) -> Unit
    ): PlaybackErrorHandling {
        if (isRepairing) return PlaybackErrorHandling.AlreadyRepairing

        val type = PlaybackFailureClassifier.classify(error)
        val coordinator = coordinator
        val kind = currentKind
        val providerId = currentProviderId
        if (coordinator == null || type != PlaybackFailureType.DECODER || kind == null || providerId == null) {
            return PlaybackErrorHandling.NotDecoder(type)
        }

        val failedTrack = PlaybackFailureClassifier.failedTrack(error)
        val myGeneration = generation
        val memorized = wasUsingMemorizedPlan
        wasUsingMemorizedPlan = false

        job = scope.launch {
            val outcome = coordinator.recoverFromDecodingFailure(
                kind = kind,
                providerId = providerId,
                wasUsingMemorizedPlan = memorized,
                initialFailedTrack = failedTrack,
                restore = restore,
                exhaustionListener = exhaustionListener
            )
            if (myGeneration != generation) return@launch // callback d'une génération obsolète (R3)
            job = null
            onOutcome(outcome)
        }
        return PlaybackErrorHandling.RepairStarted
    }

    /**
     * Prépare un « Réessayer » complet (bouton d'échec final, §7.2/§7.4, review R7) : annule tout
     * état résiduel et remet explicitement le moteur au plan par défaut — sans ça, le moteur
     * restait sur la dernière stratégie/override en échec et le bouton ne rejouait pas
     * nécessairement la séquence depuis son point de départ. L'appelant doit ensuite re-poser son
     * `MediaItem` si nécessaire et appeler `prepare()`/`play()` sur `engineController.player`.
     */
    fun prepareRetryFromScratch() {
        cancel()
        wasUsingMemorizedPlan = false
        engine?.prepareInitialPlan(PlaybackRepairPlan.DEFAULT)
    }
}
