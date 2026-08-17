package com.cstv.app.presentation.player.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

/**
 * F39 §8.5/§8.8, tâche 4 : bascule transactionnelle, patron `FakePlaybackEngine` de T23 réutilisé
 * pour un moteur déterministe — aucun device requis (AGENTS.md).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MediaVersionSwitchControllerTest {

    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)

    private val previous = PlayableVersionTarget(mediaUrl = "https://x/previous.mkv")
    private val target = PlayableVersionTarget(mediaUrl = "https://x/target.mkv")

    @Test
    fun `a successful switch keeps the source position`() = runTest {
        val engine = FakeMediaVersionSwitchEngine(
            scripts = listOf(FakeMediaVersionSwitchEngine.readyImmediately()),
            initialPositionMs = 42_000L,
            initialPlayWhenReady = true,
            initialSpeed = 1.5f,
            targetDurationMs = 120_000L
        )
        val controller = MediaVersionSwitchController(engine)

        val result = controller.switchTo(previous, target)

        assertEquals(MediaVersionSwitchResult.Switched, result)
        assertEquals(listOf(target), engine.preparedTargets)
        assertEquals(listOf(42_000L), engine.seeks)
        assertEquals(listOf(true), engine.playWhenReadyChanges)
        assertEquals(listOf(1.5f), engine.speedChanges)
    }

    @Test
    fun `a target shorter than the source position seeks near its end, not its start`() = runTest {
        val engine = FakeMediaVersionSwitchEngine(
            scripts = listOf(FakeMediaVersionSwitchEngine.readyImmediately()),
            initialPositionMs = 100_000L,
            targetDurationMs = 60_000L // cible plus courte que la position source
        )
        val controller = MediaVersionSwitchController(engine)

        controller.switchTo(previous, target)

        // min(positionSource, max(0, durationCible - 2s)) = min(100_000, 58_000) = 58_000.
        assertEquals(listOf(58_000L), engine.seeks)
    }

    @Test
    fun `a target far shorter than the 2s margin seeks to zero, never negative`() = runTest {
        val engine = FakeMediaVersionSwitchEngine(
            scripts = listOf(FakeMediaVersionSwitchEngine.readyImmediately()),
            initialPositionMs = 100_000L,
            targetDurationMs = 1_000L // durée - marge serait négative
        )
        val controller = MediaVersionSwitchController(engine)

        controller.switchTo(previous, target)

        assertEquals(listOf(0L), engine.seeks)
    }

    @Test
    fun `a timeout rolls back to the previous source and its position, without touching the preference`() = runTest {
        val engine = FakeMediaVersionSwitchEngine(
            scripts = listOf(
                FakeMediaVersionSwitchEngine.neverResponds(), // cible : ne répond jamais -> timeout 8s
                FakeMediaVersionSwitchEngine.readyImmediately() // rollback : reconstruit la source précédente
            ),
            initialPositionMs = 42_000L,
            initialPlayWhenReady = true
        )
        val controller = MediaVersionSwitchController(engine)
        var preferenceCommitted = false

        val result = controller.switchTo(previous, target) { preferenceCommitted = true }

        assertEquals(MediaVersionSwitchResult.RolledBack, result)
        assertEquals(listOf(target, previous), engine.preparedTargets)
        assertEquals(listOf(42_000L), engine.seeks) // position d'origine restaurée
        assertFalse("la préférence série ne doit jamais être modifiée sur un rollback", preferenceCommitted)
    }

    @Test
    fun `an explicit engine failure rolls back just like a timeout`() = runTest {
        val engine = FakeMediaVersionSwitchEngine(
            scripts = listOf(
                FakeMediaVersionSwitchEngine.failureImmediately(),
                FakeMediaVersionSwitchEngine.readyImmediately()
            ),
            initialPositionMs = 10_000L
        )
        val controller = MediaVersionSwitchController(engine)

        val result = controller.switchTo(previous, target)

        assertEquals(MediaVersionSwitchResult.RolledBack, result)
        assertEquals(listOf(10_000L), engine.seeks)
    }

    @Test
    fun `success commits the series preference exactly once`() = runTest {
        val engine = FakeMediaVersionSwitchEngine(scripts = listOf(FakeMediaVersionSwitchEngine.readyImmediately()))
        val controller = MediaVersionSwitchController(engine)
        var commits = 0

        controller.switchTo(previous, target) { commits++ }

        assertEquals(1, commits)
    }

    @Test
    fun `a rapid second switch supersedes a still-pending first one, which never touches playback afterwards`() = runTest {
        val targetA = PlayableVersionTarget(mediaUrl = "https://x/a.mkv")
        val targetB = PlayableVersionTarget(mediaUrl = "https://x/b.mkv")
        val engine = FakeMediaVersionSwitchEngine(
            scripts = listOf(
                FakeMediaVersionSwitchEngine.readyAfter(4_000L), // A : Ready tardif, dans le délai des 8s
                FakeMediaVersionSwitchEngine.readyImmediately()   // B : Ready immédiat
            ),
            initialPositionMs = 5_000L
        )
        val controller = MediaVersionSwitchController(engine)

        var resultA: MediaVersionSwitchResult? = null
        var commitsA = 0
        var commitsB = 0
        val jobA = launch { resultA = controller.switchTo(previous, targetA) { commitsA++ } }
        advanceTimeBy(100) // laisse A démarrer son prepareTarget avant que B ne parte.

        val resultB = controller.switchTo(previous, targetB) { commitsB++ }
        jobA.join()

        assertEquals(MediaVersionSwitchResult.Switched, resultB)
        assertEquals(MediaVersionSwitchResult.Superseded, resultA)
        assertEquals(1, commitsB)
        assertEquals("la préférence n'est jamais commise pour une bascule supplantée", 0, commitsA)
        // A n'a jamais seek/rollback après avoir découvert qu'elle était supplantée : seuls les
        // seeks de B (source unique, ici la position initiale inchangée) apparaissent.
        assertEquals(listOf(5_000L), engine.seeks)
    }

    @Test
    fun `readiness arriving just under the timeout still succeeds, tolerating a concurrent T23 recovery`() = runTest {
        val engine = FakeMediaVersionSwitchEngine(
            scripts = listOf(FakeMediaVersionSwitchEngine.readyAfter(7_900L)) // sous les 8 s du timeout
        )
        val controller = MediaVersionSwitchController(engine)

        val result = controller.switchTo(previous, target)

        assertEquals(MediaVersionSwitchResult.Switched, result)
    }
}
