package com.cstv.app.data.remote.api

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

@OptIn(ExperimentalCoroutinesApi::class)
class XtreamRequestGateTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)


    @Test
    fun test_foregroundCall_executesImmediately_withNoYield() = runTest {
        val gate = XtreamRequestGate()

        val result = gate.acquire { "done" }

        assertEquals("done", result)
    }

    @Test
    fun test_backgroundCall_executesImmediately_whenNoForegroundActive() = runTest {
        val gate = XtreamRequestGate()

        val result = withContext(RequestPriority.background) {
            gate.acquire { "done" }
        }

        assertEquals("done", result)
    }

    @Test
    fun test_backgroundCall_waitsForForegroundToFinish() = runTest {
        val gate = XtreamRequestGate()
        val events = mutableListOf<String>()

        // Une requête écran (foreground) "en cours" plus longue que le
        // travail d'arrière-plan qui essaie de démarrer pendant ce temps.
        val foregroundJob = async {
            gate.acquire {
                events.add("foreground_start")
                delay(270) // volontairement décalé du pas de sondage (100ms) du gate
                events.add("foreground_end")
            }
        }

        // Laisse le foreground s'enregistrer avant de lancer le background.
        delay(50)

        val backgroundJob = async {
            withContext(RequestPriority.background) {
                gate.acquire {
                    events.add("background_start")
                }
            }
        }

        foregroundJob.await()
        backgroundJob.await()

        // Le background ne doit jamais démarrer avant la fin du foreground.
        assertEquals(
            listOf("foreground_start", "foreground_end", "background_start"),
            events
        )
    }

    @Test
    fun test_throttleWindow_expiresOnItsOwn() {
        var now = 0L
        val gate = XtreamRequestGate().apply { nowMs = { now } }

        gate.notifyThrottled()
        assertTrue(gate.isThrottled())

        now = 29_000L
        assertTrue(gate.isThrottled())

        now = 31_000L
        assertFalse(gate.isThrottled())
    }

    @Test
    fun test_freshGate_isNotThrottled() {
        assertFalse(XtreamRequestGate().isThrottled())
    }

    @Test
    fun test_backgroundCall_waitsOutTheThrottleWindow() = runTest {
        var now = 0L
        val gate = XtreamRequestGate().apply { nowMs = { now } }
        gate.notifyThrottled()

        var ran = false
        val backgroundJob = async {
            withContext(RequestPriority.background) {
                gate.acquire { ran = true }
            }
        }

        delay(1_000)
        assertFalse("le trafic de fond doit rester en attente pendant la fenêtre", ran)

        now = 31_000L
        backgroundJob.await()
        assertTrue(ran)
    }

    @Test
    fun test_foregroundCall_ignoresTheThrottleWindow() = runTest {
        val gate = XtreamRequestGate().apply { nowMs = { 0L } }
        gate.notifyThrottled()

        val result = gate.acquire { "done" }

        assertEquals("done", result)
    }

    @Test
    fun test_backgroundCalls_neverOverlapEachOther() = runTest {
        val gate = XtreamRequestGate()
        var inFlight = 0
        var maxInFlight = 0

        val jobs = (1..4).map {
            async {
                withContext(RequestPriority.background) {
                    gate.acquire {
                        inFlight++
                        maxInFlight = maxOf(maxInFlight, inFlight)
                        delay(50)
                        inFlight--
                    }
                }
            }
        }
        jobs.forEach { it.await() }

        assertEquals("le trafic de fond doit rester séquentiel", 1, maxInFlight)
    }

    @Test
    fun test_currentLevel_defaultsToForeground_withoutExplicitContext() = runTest {
        assertEquals(RequestPriority.Level.FOREGROUND, RequestPriority.currentLevel())
    }

    @Test
    fun test_currentLevel_isBackground_underBackgroundContext() = runTest {
        val level = withContext(RequestPriority.background) {
            RequestPriority.currentLevel()
        }

        assertEquals(RequestPriority.Level.BACKGROUND, level)
    }
}
