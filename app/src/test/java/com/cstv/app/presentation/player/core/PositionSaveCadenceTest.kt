package com.cstv.app.presentation.player.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

class PositionSaveCadenceTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)


    @Test
    fun `saves after five seconds of valid playback`() {
        val cadence = PositionSaveCadence(saveIntervalMs = 5_000L)

        assertFalse(cadence.shouldSave(positionMs = 1_000L, durationMs = 10_000L, nowMs = 0L))
        assertFalse(cadence.shouldSave(positionMs = 2_000L, durationMs = 10_000L, nowMs = 4_999L))
        assertTrue(cadence.shouldSave(positionMs = 6_000L, durationMs = 10_000L, nowMs = 5_000L))
    }

    @Test
    fun `pause and resume do not accelerate save cadence`() {
        val cadence = PositionSaveCadence(saveIntervalMs = 5_000L)

        assertFalse(cadence.shouldSave(positionMs = 1_000L, durationMs = 10_000L, nowMs = 0L))
        assertFalse(cadence.shouldSave(positionMs = 2_000L, durationMs = 10_000L, nowMs = 1_000L))
        // Reprise bien plus tard : seule la durée réellement écoulée déclenche
        // la sauvegarde, indépendamment du nombre de boucles précédentes.
        assertTrue(cadence.shouldSave(positionMs = 3_000L, durationMs = 10_000L, nowMs = 6_000L))
    }

    @Test
    fun `invalid snapshots do not advance save cadence`() {
        val cadence = PositionSaveCadence(saveIntervalMs = 2_000L)

        assertFalse(cadence.shouldSave(positionMs = 0L, durationMs = 10_000L, nowMs = 0L))
        assertFalse(cadence.shouldSave(positionMs = 1_000L, durationMs = 0L, nowMs = 1_000L))
        assertFalse(cadence.shouldSave(positionMs = 1_000L, durationMs = 10_000L, nowMs = 2_000L))
        assertTrue(cadence.shouldSave(positionMs = 2_000L, durationMs = 10_000L, nowMs = 4_000L))
    }
}
