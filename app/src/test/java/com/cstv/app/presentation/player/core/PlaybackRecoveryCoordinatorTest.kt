package com.cstv.app.presentation.player.core

import com.cstv.app.domain.model.DecoderStrategy
import com.cstv.app.domain.model.PlaybackRepairPlan
import com.cstv.app.domain.model.TrackFingerprint
import com.cstv.app.domain.model.TrackKind
import com.cstv.app.domain.repository.PlaybackRepairRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

/** Enregistre les appels sans mock — même motif que `PlaybackLockManagerTest` (objet anonyme). */
private class RecordingRepairRepository(
    private var stored: PlaybackRepairPlan? = null
) : PlaybackRepairRepository {
    val saved = mutableListOf<PlaybackRepairPlan>()
    var cleared = false

    override suspend fun getRepairPlan(kind: String, providerId: Int): PlaybackRepairPlan? = stored

    override suspend fun saveRepairPlan(kind: String, providerId: Int, plan: PlaybackRepairPlan) {
        saved.add(plan)
        stored = plan
    }

    override suspend fun clearRepairPlan(kind: String, providerId: Int) {
        cleared = true
        stored = null
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackRecoveryCoordinatorTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)

    private val restore = PlaybackRestoreState(positionMs = 1_000L, playWhenReady = true, speed = 1f)
    private val audioTrack = TrackFingerprint(TrackKind.AUDIO, "fra", "audio/eac3", "ec-3", 6, 0, "VFF")
    private val alternateTrack = TrackFingerprint(TrackKind.AUDIO, "eng", "audio/ac3", "ac-3", 2, 0, "VO")

    // --- Ordre exact des trois essais / succès à chaque étape ---

    @Test
    fun sequence_succeedsAtStep1_neverTriesStep2Or3() = runTest {
        val engine = FakePlaybackEngine(listOf(FakePlaybackEngine.readyImmediately()))
        val repository = RecordingRepairRepository()
        val coordinator = PlaybackRecoveryCoordinator(engine, repository)

        val outcome = coordinator.recoverFromDecodingFailure("movie", 1, wasUsingMemorizedPlan = false, initialFailedTrack = audioTrack, restore = restore)

        val expectedPlan = PlaybackRepairPlan(decoderStrategy = DecoderStrategy.SOFTWARE_PREFERRED, softwarePreferredTrackKind = TrackKind.AUDIO)
        assertEquals(RecoveryOutcome.Stable(expectedPlan), outcome)
        assertEquals(1, engine.appliedPlans.size)
        assertEquals(listOf(expectedPlan), repository.saved)
    }

    @Test
    fun sequence_step1Fails_step2Succeeds_disablesTheIdentifiedTrack() = runTest {
        val engine = FakePlaybackEngine(
            listOf(FakePlaybackEngine.rendererFailureImmediately(audioTrack), FakePlaybackEngine.readyImmediately())
        )
        val coordinator = PlaybackRecoveryCoordinator(engine, RecordingRepairRepository())

        val outcome = coordinator.recoverFromDecodingFailure("movie", 1, wasUsingMemorizedPlan = false, initialFailedTrack = audioTrack, restore = restore)

        val expectedPlan = PlaybackRepairPlan(decoderStrategy = DecoderStrategy.SOFTWARE_PREFERRED, softwarePreferredTrackKind = TrackKind.AUDIO, disabledTrack = audioTrack)
        assertEquals(RecoveryOutcome.Stable(expectedPlan), outcome)
        assertEquals(2, engine.appliedPlans.size)
        assertEquals(audioTrack, engine.appliedPlans[1].disabledTrack)
    }

    @Test
    fun sequence_step1And2Fail_step3Succeeds_selectsAlternateAudio() = runTest {
        val engine = FakePlaybackEngine(
            scripts = listOf(
                FakePlaybackEngine.rendererFailureImmediately(audioTrack),
                FakePlaybackEngine.rendererFailureImmediately(audioTrack),
                FakePlaybackEngine.readyImmediately()
            ),
            alternateAudioTrack = alternateTrack
        )
        val coordinator = PlaybackRecoveryCoordinator(engine, RecordingRepairRepository())

        val outcome = coordinator.recoverFromDecodingFailure("series", 2, wasUsingMemorizedPlan = false, initialFailedTrack = audioTrack, restore = restore)

        val expectedPlan = PlaybackRepairPlan(decoderStrategy = DecoderStrategy.SOFTWARE_PREFERRED, softwarePreferredTrackKind = TrackKind.AUDIO, preferredAudio = alternateTrack)
        assertEquals(RecoveryOutcome.Stable(expectedPlan), outcome)
        assertEquals(3, engine.appliedPlans.size)
        // Étape 3 réactive la piste fautive (pas de disabledTrack) et pose l'alternative.
        assertNull(engine.appliedPlans[2].disabledTrack)
        assertEquals(listOf(audioTrack), engine.alternateAudioTrackExclusions)
    }

    @Test
    fun sequence_allThreeStepsFail_returnsDecoderExhausted_withoutSavingAnything() = runTest {
        val engine = FakePlaybackEngine(
            scripts = listOf(
                FakePlaybackEngine.rendererFailureImmediately(audioTrack),
                FakePlaybackEngine.rendererFailureImmediately(audioTrack),
                FakePlaybackEngine.rendererFailureImmediately(alternateTrack)
            ),
            alternateAudioTrack = alternateTrack
        )
        val repository = RecordingRepairRepository()
        val coordinator = PlaybackRecoveryCoordinator(engine, repository)

        val outcome = coordinator.recoverFromDecodingFailure("movie", 1, wasUsingMemorizedPlan = false, initialFailedTrack = audioTrack, restore = restore)

        assertEquals(RecoveryOutcome.DecoderExhausted, outcome)
        assertTrue(repository.saved.isEmpty())
    }

    // --- Cas limite : piste fautive non identifiée -> séquence à l'aveugle, étape 2 sautée ---

    @Test
    fun sequence_noIdentifiedTrack_skipsStep2_goesStraightFromStep1ToStep3() = runTest {
        val engine = FakePlaybackEngine(
            scripts = listOf(FakePlaybackEngine.rendererFailureImmediately(null), FakePlaybackEngine.readyImmediately()),
            alternateAudioTrack = alternateTrack
        )
        val coordinator = PlaybackRecoveryCoordinator(engine, RecordingRepairRepository())

        val outcome = coordinator.recoverFromDecodingFailure("movie", 1, wasUsingMemorizedPlan = false, initialFailedTrack = null, restore = restore)

        assertEquals(RecoveryOutcome.Stable(PlaybackRepairPlan(decoderStrategy = DecoderStrategy.SOFTWARE_PREFERRED, preferredAudio = alternateTrack)), outcome)
        // Deux essais seulement : étape 1 puis étape 3, étape 2 sautée faute de piste identifiée.
        assertEquals(2, engine.appliedPlans.size)
    }

    // --- Cas limite : pas d'autre piste audio disponible -> étape 3 sautée, échec final ---

    @Test
    fun sequence_noAlternateAudioTrack_step3Skipped_decoderExhausted() = runTest {
        val engine = FakePlaybackEngine(
            scripts = listOf(FakePlaybackEngine.rendererFailureImmediately(audioTrack), FakePlaybackEngine.rendererFailureImmediately(audioTrack)),
            alternateAudioTrack = null
        )
        val coordinator = PlaybackRecoveryCoordinator(engine, RecordingRepairRepository())

        val outcome = coordinator.recoverFromDecodingFailure("movie", 1, wasUsingMemorizedPlan = false, initialFailedTrack = audioTrack, restore = restore)

        assertEquals(RecoveryOutcome.DecoderExhausted, outcome)
        assertEquals(2, engine.appliedPlans.size)
    }

    // --- NonDecoderFailure (R9) : interrompt immédiatement, ne masque pas une panne réseau/live-window ---

    @Test
    fun nonDecoderFailureDuringAnAttempt_abortsTheSequenceImmediately_doesNotTryRemainingSteps() = runTest {
        val engine = FakePlaybackEngine(listOf(FakePlaybackEngine.nonDecoderFailureImmediately(PlaybackFailureType.NETWORK_SOURCE)))
        val coordinator = PlaybackRecoveryCoordinator(engine, RecordingRepairRepository())

        val outcome = coordinator.recoverFromDecodingFailure("movie", 1, wasUsingMemorizedPlan = false, initialFailedTrack = audioTrack, restore = restore)

        assertEquals(RecoveryOutcome.Aborted(PlaybackFailureType.NETWORK_SOURCE), outcome)
        assertEquals(1, engine.appliedPlans.size)
    }

    @Test
    fun liveWindowFailureDuringAnAttempt_abortsWithItsOwnType_ratherThanADeviceFailure() = runTest {
        // R9 : avant, tout non-RendererFailure devenait un unique échec « appareil » — un incident
        // live-window survenu pendant un essai de réparation n'en est pas un.
        val engine = FakePlaybackEngine(listOf(FakePlaybackEngine.nonDecoderFailureImmediately(PlaybackFailureType.LIVE_WINDOW)))
        val coordinator = PlaybackRecoveryCoordinator(engine, RecordingRepairRepository())

        val outcome = coordinator.recoverFromDecodingFailure("live", 42, wasUsingMemorizedPlan = false, initialFailedTrack = audioTrack, restore = restore)

        assertEquals(RecoveryOutcome.Aborted(PlaybackFailureType.LIVE_WINDOW), outcome)
    }

    // --- Timeout par essai : un essai qui ne répond jamais passe à l'étape suivante ---

    @Test
    fun attemptTimeout_movesOnToTheNextStep() = runTest {
        val engine = FakePlaybackEngine(listOf(FakePlaybackEngine.neverResponds(), FakePlaybackEngine.readyImmediately()))
        val coordinator = PlaybackRecoveryCoordinator(engine, RecordingRepairRepository())

        val outcome = coordinator.recoverFromDecodingFailure("movie", 1, wasUsingMemorizedPlan = false, initialFailedTrack = audioTrack, restore = restore)

        val expectedPlan = PlaybackRepairPlan(decoderStrategy = DecoderStrategy.SOFTWARE_PREFERRED, softwarePreferredTrackKind = TrackKind.AUDIO, disabledTrack = audioTrack)
        assertEquals(RecoveryOutcome.Stable(expectedPlan), outcome)
    }

    // --- Échec pendant la fenêtre de stabilité : Ready seul ne suffit pas ---

    @Test
    fun failureDuringStabilityWindow_doesNotCountAsSuccess() = runTest {
        val engine = FakePlaybackEngine(
            listOf(FakePlaybackEngine.readyThenFailsBeforeStable(afterMs = 1_000L, failedTrack = audioTrack), FakePlaybackEngine.readyImmediately())
        )
        val coordinator = PlaybackRecoveryCoordinator(engine, RecordingRepairRepository())

        val outcome = coordinator.recoverFromDecodingFailure("movie", 1, wasUsingMemorizedPlan = false, initialFailedTrack = audioTrack, restore = restore)

        // L'étape 1 a échoué pendant la fenêtre de stabilité : l'étape 2 a bien été tentée.
        assertEquals(2, engine.appliedPlans.size)
        val expectedPlan = PlaybackRepairPlan(decoderStrategy = DecoderStrategy.SOFTWARE_PREFERRED, softwarePreferredTrackKind = TrackKind.AUDIO, disabledTrack = audioTrack)
        assertEquals(RecoveryOutcome.Stable(expectedPlan), outcome)
    }

    // --- Timeout global : la séquence ne dépasse jamais 24 s cumulées ---

    @Test
    fun globalSequenceTimeout_stopsRetrying_returnsDecoderExhausted() = runTest {
        // Trois essais qui pendent chacun jusqu'au timeout d'essai (8 s) épuisent le budget
        // global (24 s) exactement au dernier essai : aucun sursis, échec final.
        val engine = FakePlaybackEngine(listOf(FakePlaybackEngine.neverResponds(), FakePlaybackEngine.neverResponds(), FakePlaybackEngine.neverResponds()))
        val coordinator = PlaybackRecoveryCoordinator(engine, RecordingRepairRepository())

        val outcome = coordinator.recoverFromDecodingFailure("movie", 1, wasUsingMemorizedPlan = false, initialFailedTrack = audioTrack, restore = restore)

        assertEquals(RecoveryOutcome.DecoderExhausted, outcome)
    }

    // --- Annulation propre à la fermeture du lecteur ---

    @Test
    fun cancellingTheCallerCoroutine_cancelsTheRecoveryCleanly() = runTest {
        val engine = FakePlaybackEngine(listOf(FakePlaybackEngine.neverResponds()))
        val coordinator = PlaybackRecoveryCoordinator(engine, RecordingRepairRepository())

        // `neverResponds()` pendrait jusqu'au timeout d'essai (8 s) sans intervention : le test
        // vérifie que fermer le lecteur avant ça (cancel) arrête proprement la coroutine
        // englobante, sans figer le drainage du scheduler virtuel (AGENTS.md § boucles infinies).
        val job = async {
            coordinator.recoverFromDecodingFailure("movie", 1, wasUsingMemorizedPlan = false, initialFailedTrack = audioTrack, restore = restore)
        }
        runCurrent()
        job.cancel()
        assertTrue(job.isCancelled)
    }

    // --- Profil mémorisé : essayé en premier, supprimé s'il échoue, séquence repart de zéro ---

    @Test
    fun initialPlan_returnsMemorizedPlan_whenOnePersisted() = runTest {
        val memorized = PlaybackRepairPlan(decoderStrategy = DecoderStrategy.SOFTWARE_PREFERRED, disabledTrack = audioTrack)
        val repository = RecordingRepairRepository(stored = memorized)
        val coordinator = PlaybackRecoveryCoordinator(FakePlaybackEngine(emptyList()), repository)

        assertEquals(memorized, coordinator.initialPlan("movie", 1))
    }

    @Test
    fun initialPlan_returnsDefault_whenNothingMemorized() = runTest {
        val coordinator = PlaybackRecoveryCoordinator(FakePlaybackEngine(emptyList()), RecordingRepairRepository(stored = null))

        assertEquals(PlaybackRepairPlan.DEFAULT, coordinator.initialPlan("movie", 1))
    }

    @Test
    fun memorizedPlanFails_isDeleted_thenSequenceRestartsFromDefault() = runTest {
        val engine = FakePlaybackEngine(listOf(FakePlaybackEngine.readyImmediately()))
        val repository = RecordingRepairRepository(stored = PlaybackRepairPlan(decoderStrategy = DecoderStrategy.SOFTWARE_PREFERRED, disabledTrack = audioTrack))
        val coordinator = PlaybackRecoveryCoordinator(engine, repository)

        val outcome = coordinator.recoverFromDecodingFailure("movie", 1, wasUsingMemorizedPlan = true, initialFailedTrack = audioTrack, restore = restore)

        assertTrue(repository.cleared)
        // La séquence repart de l'étape 1 (SOFTWARE_PREFERRED sans piste désactivée), pas du plan mémorisé.
        val expectedPlan = PlaybackRepairPlan(decoderStrategy = DecoderStrategy.SOFTWARE_PREFERRED, softwarePreferredTrackKind = TrackKind.AUDIO)
        assertEquals(expectedPlan, engine.appliedPlans.single())
        assertEquals(RecoveryOutcome.Stable(expectedPlan), outcome)
    }

    // --- Restauration de position VOD ---

    @Test
    fun vodRestorePositionMs_clampsToTwoSecondsBeforeTheEnd() {
        assertEquals(58_000L, PlaybackRecoveryCoordinator.vodRestorePositionMs(positionBeforeErrorMs = 58_000L, durationMs = 60_000L))
        assertEquals(58_000L, PlaybackRecoveryCoordinator.vodRestorePositionMs(positionBeforeErrorMs = 59_500L, durationMs = 60_000L))
    }

    @Test
    fun vodRestorePositionMs_neverNegative_forAVeryShortMedia() {
        assertEquals(0L, PlaybackRecoveryCoordinator.vodRestorePositionMs(positionBeforeErrorMs = 500L, durationMs = 1_000L))
    }

    @Test
    fun vodRestorePositionMs_negativePosition_clampsToZero() {
        assertEquals(0L, PlaybackRecoveryCoordinator.vodRestorePositionMs(positionBeforeErrorMs = -1L, durationMs = 60_000L))
    }

    // --- Tâche 6 : point d'extension F40/F39 — notification à l'épuisement ---

    @Test
    fun exhaustionListener_notifiedExactlyOnce_whenDecodingReallyExhausts() = runTest {
        // R9 : la notification n'est due que sur un vrai épuisement de décodage, pas sur un
        // Aborted (réseau/live-window/inconnu) — voir le test suivant.
        val engine = FakePlaybackEngine(
            listOf(FakePlaybackEngine.rendererFailureImmediately(audioTrack), FakePlaybackEngine.rendererFailureImmediately(audioTrack)),
            alternateAudioTrack = null
        )
        val coordinator = PlaybackRecoveryCoordinator(engine, RecordingRepairRepository())
        val notifications = mutableListOf<Pair<String, Int>>()
        val listener = PlaybackRecoveryExhaustionListener { kind, providerId -> notifications.add(kind to providerId) }

        val outcome = coordinator.recoverFromDecodingFailure("live", 42, wasUsingMemorizedPlan = false, initialFailedTrack = audioTrack, restore = restore, exhaustionListener = listener)

        assertEquals(RecoveryOutcome.DecoderExhausted, outcome)
        assertEquals(listOf("live" to 42), notifications)
    }

    @Test
    fun exhaustionListener_neverNotified_whenSequenceIsAborted_notReallyExhausted() = runTest {
        // R9 : un Aborted (réseau/live-window survenu pendant un essai) n'est pas un épuisement de
        // décodage — ne doit pas déclencher le seam F39/F40 ni le message « illisible sur cet appareil ».
        val engine = FakePlaybackEngine(listOf(FakePlaybackEngine.nonDecoderFailureImmediately(PlaybackFailureType.NETWORK_SOURCE)))
        val coordinator = PlaybackRecoveryCoordinator(engine, RecordingRepairRepository())
        var notified = false
        val listener = PlaybackRecoveryExhaustionListener { _, _ -> notified = true }

        val outcome = coordinator.recoverFromDecodingFailure("live", 42, wasUsingMemorizedPlan = false, initialFailedTrack = audioTrack, restore = restore, exhaustionListener = listener)

        assertEquals(RecoveryOutcome.Aborted(PlaybackFailureType.NETWORK_SOURCE), outcome)
        assertTrue(!notified)
    }

    @Test
    fun exhaustionListener_neverNotified_whenSequenceSucceeds() = runTest {
        val engine = FakePlaybackEngine(listOf(FakePlaybackEngine.readyImmediately()))
        val coordinator = PlaybackRecoveryCoordinator(engine, RecordingRepairRepository())
        var notified = false
        val listener = PlaybackRecoveryExhaustionListener { _, _ -> notified = true }

        val outcome = coordinator.recoverFromDecodingFailure("live", 42, wasUsingMemorizedPlan = false, initialFailedTrack = audioTrack, restore = restore, exhaustionListener = listener)

        assertTrue(outcome is RecoveryOutcome.Stable)
        assertTrue(!notified)
    }

    @Test
    fun exhaustionListener_absent_noRegressionOnVodOrSeries_whichHaveNoF40Consumer() = runTest {
        val engine = FakePlaybackEngine(
            listOf(FakePlaybackEngine.rendererFailureImmediately(audioTrack), FakePlaybackEngine.rendererFailureImmediately(audioTrack)),
            alternateAudioTrack = null
        )
        val coordinator = PlaybackRecoveryCoordinator(engine, RecordingRepairRepository())

        // Aucun listener passé (comportement VOD/série, tâche 7) : aucune exception, comportement
        // identique à avant la tâche 6.
        val outcome = coordinator.recoverFromDecodingFailure("movie", 1, wasUsingMemorizedPlan = false, initialFailedTrack = audioTrack, restore = restore)

        assertEquals(RecoveryOutcome.DecoderExhausted, outcome)
    }

    // --- Tâche 6 : point d'extension F41 — position de tampon en direct ---

    @Test
    fun liveRestorePositionMs_usesTheSuppliedBufferPosition_overLiveEdge() {
        val provider = LiveBufferPositionProvider { _, _ -> 12_345L }

        assertEquals(12_345L, PlaybackRecoveryCoordinator.liveRestorePositionMs("live", 42, provider))
    }

    @Test
    fun liveRestorePositionMs_noProvider_fallsBackToLiveEdge() {
        assertNull(PlaybackRecoveryCoordinator.liveRestorePositionMs("live", 42, provider = null))
    }

    @Test
    fun liveRestorePositionMs_providerWithoutAValue_fallsBackToLiveEdge() {
        val provider = LiveBufferPositionProvider { _, _ -> null }

        assertNull(PlaybackRecoveryCoordinator.liveRestorePositionMs("live", 42, provider))
    }
}
