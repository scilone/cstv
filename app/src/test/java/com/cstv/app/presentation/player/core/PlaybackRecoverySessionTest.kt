package com.cstv.app.presentation.player.core

import android.os.SystemClock
import android.text.TextUtils
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlaybackException
import com.cstv.app.domain.model.DecoderStrategy
import com.cstv.app.domain.model.PlaybackRepairPlan
import com.cstv.app.domain.repository.PlaybackRepairRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.Mockito
import java.io.IOException

/**
 * Couvre les correctifs de la review T23 §12 portés par [PlaybackRecoverySession] : génération
 * monotonique (R3), remise à zéro par cible (R4), qualification avant tout repli côté appelant
 * (R6, R8), et « Réessayer » qui repart bien de zéro (R7). [engine] est laissé `null` dans tous ces
 * tests : ces comportements ne dépendent pas de l'adapter Media3 (non testable en JVM, AGENTS.md).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackRecoverySessionTest {
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)

    private class RecordingRepairRepository(private var stored: PlaybackRepairPlan? = null) : PlaybackRepairRepository {
        var cleared = false
        override suspend fun getRepairPlan(kind: String, providerId: Int): PlaybackRepairPlan? = stored
        override suspend fun saveRepairPlan(kind: String, providerId: Int, plan: PlaybackRepairPlan) { stored = plan }
        override suspend fun clearRepairPlan(kind: String, providerId: Int) { cleared = true; stored = null }
    }

    private fun <T> withAndroidStubsMocked(block: () -> T): T =
        Mockito.mockStatic(TextUtils::class.java).use { textUtils ->
            textUtils.`when`<Boolean> { TextUtils.isEmpty(org.mockito.ArgumentMatchers.any()) }.thenAnswer { inv -> (inv.arguments[0] as? CharSequence).isNullOrEmpty() }
            Mockito.mockStatic(SystemClock::class.java).use { systemClock ->
                systemClock.`when`<Long> { SystemClock.elapsedRealtime() }.thenReturn(0L)
                block()
            }
        }

    private fun decoderError(): PlaybackException = withAndroidStubsMocked {
        ExoPlaybackException.createForRenderer(RuntimeException("boom"), "AudioRenderer", 0, null, 0, false, PlaybackException.ERROR_CODE_DECODING_FAILED)
    }

    private fun networkError(): PlaybackException = withAndroidStubsMocked {
        ExoPlaybackException.createForSource(IOException("boom"), PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
    }

    private val restore = PlaybackRestoreState(positionMs = 0L, playWhenReady = true, speed = 1f)

    // --- R8 : la qualification précède tout repli côté appelant ---

    @Test
    fun handleError_networkFailure_returnsNotDecoder_withoutStartingAnyJob() = runTest {
        val coordinator = PlaybackRecoveryCoordinator(FakePlaybackEngine(emptyList()), RecordingRepairRepository())
        val session = PlaybackRecoverySession(this, engine = null, coordinator = coordinator)
        session.forTarget("movie", 1)

        val handling = session.handleError(networkError(), restore) { error("onOutcome ne doit pas être appelé") }

        assertTrue(handling is PlaybackErrorHandling.NotDecoder)
        assertEquals(PlaybackFailureType.NETWORK_SOURCE, (handling as PlaybackErrorHandling.NotDecoder).type)
        assertFalse(session.isRepairing)
    }

    @Test
    fun handleError_noCoordinatorInjected_returnsNotDecoder_evenForADecoderError() = runTest {
        // Comportement pré-T23 inchangé quand le repository n'est pas injecté (§8.5, tâche 3).
        val session = PlaybackRecoverySession(this, engine = null, coordinator = null)
        session.forTarget("movie", 1)

        val handling = session.handleError(decoderError(), restore) { error("onOutcome ne doit pas être appelé") }

        assertTrue(handling is PlaybackErrorHandling.NotDecoder)
    }

    @Test
    fun handleError_beforeAnyForTarget_returnsNotDecoder_ratherThanCrashing() = runTest {
        val coordinator = PlaybackRecoveryCoordinator(FakePlaybackEngine(emptyList()), RecordingRepairRepository())
        val session = PlaybackRecoverySession(this, engine = null, coordinator = coordinator)

        val handling = session.handleError(decoderError(), restore) { error("onOutcome ne doit pas être appelé") }

        assertTrue(handling is PlaybackErrorHandling.NotDecoder)
    }

    // --- Démarrage et issue d'une réparation ---

    @Test
    fun handleError_decoderFailure_startsRepair_andDeliversTheOutcome() = runTest {
        val engine = FakePlaybackEngine(listOf(FakePlaybackEngine.readyImmediately()))
        val coordinator = PlaybackRecoveryCoordinator(engine, RecordingRepairRepository())
        val session = PlaybackRecoverySession(this, engine = null, coordinator = coordinator)
        session.forTarget("movie", 1)

        var delivered: RecoveryOutcome? = null
        val handling = session.handleError(decoderError(), restore) { delivered = it }
        assertEquals(PlaybackErrorHandling.RepairStarted, handling)
        assertTrue(session.isRepairing)

        advanceUntilIdle() // laisse s'écouler la fenêtre de stabilité (3 s virtuelles)

        assertTrue(delivered is RecoveryOutcome.Stable)
        assertFalse(session.isRepairing)
    }

    @Test
    fun handleError_whileAlreadyRepairing_returnsAlreadyRepairing() = runTest {
        val engine = FakePlaybackEngine(listOf(FakePlaybackEngine.neverResponds()))
        val coordinator = PlaybackRecoveryCoordinator(engine, RecordingRepairRepository())
        val session = PlaybackRecoverySession(this, engine = null, coordinator = coordinator)
        session.forTarget("movie", 1)
        session.handleError(decoderError(), restore) {}
        assertTrue(session.isRepairing)

        val handling = session.handleError(decoderError(), restore) { error("onOutcome ne doit pas être appelé") }

        assertEquals(PlaybackErrorHandling.AlreadyRepairing, handling)
    }

    // --- R3 : callback d'une génération obsolète rejeté après changement de cible ---

    @Test
    fun forTarget_duringAPendingRepair_discardsItsOutcome_neverInvokingTheCallback() = runTest {
        val engine = FakePlaybackEngine(listOf(FakePlaybackEngine.neverResponds()))
        val coordinator = PlaybackRecoveryCoordinator(engine, RecordingRepairRepository())
        val session = PlaybackRecoverySession(this, engine = null, coordinator = coordinator)
        session.forTarget("movie", 1)
        var delivered: RecoveryOutcome? = null
        session.handleError(decoderError(), restore) { delivered = it }
        assertTrue(session.isRepairing)

        // Changement de média avant que l'essai en cours (qui pend indéfiniment) n'ait pu se terminer.
        session.forTarget("movie", 2)

        assertFalse(session.isRepairing)
        advanceUntilIdle()
        assertNull(delivered) // le callback de l'ancienne génération n'a jamais été livré
    }

    // --- R7 : « Réessayer » repart bien de zéro ---

    @Test
    fun prepareRetryFromScratch_cancelsAnyPendingJob_andResetsIsRepairing() = runTest {
        val engine = FakePlaybackEngine(listOf(FakePlaybackEngine.neverResponds()))
        val coordinator = PlaybackRecoveryCoordinator(engine, RecordingRepairRepository())
        val session = PlaybackRecoverySession(this, engine = null, coordinator = coordinator)
        session.forTarget("movie", 1)
        session.handleError(decoderError(), restore) {}
        assertTrue(session.isRepairing)

        session.prepareRetryFromScratch()

        assertFalse(session.isRepairing)
    }

    @Test
    fun prepareRetryFromScratch_thenNewFailure_doesNotClearAMemorizedPlanTwice() = runTest {
        val repository = RecordingRepairRepository(stored = PlaybackRepairPlan(decoderStrategy = DecoderStrategy.SOFTWARE_PREFERRED))
        val engine = FakePlaybackEngine(listOf(FakePlaybackEngine.readyImmediately()))
        val coordinator = PlaybackRecoveryCoordinator(engine, repository)
        val session = PlaybackRecoverySession(this, engine = null, coordinator = coordinator)
        session.forTarget("movie", 1) // charge le plan mémorisé (wasUsingMemorizedPlan = true en interne)

        session.prepareRetryFromScratch() // R7 : remet ce drapeau à false

        var delivered: RecoveryOutcome? = null
        session.handleError(decoderError(), restore) { delivered = it }
        advanceUntilIdle()

        // Un nouvel échec après retry n'est plus attribué à un essai du plan mémorisé : pas de
        // `clearRepairPlan` redondant pour ce cas déjà géré par `prepareRetryFromScratch`.
        assertFalse(repository.cleared)
        assertTrue(delivered is RecoveryOutcome.Stable)
    }
}
