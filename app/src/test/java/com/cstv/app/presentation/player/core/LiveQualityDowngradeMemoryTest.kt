package com.cstv.app.presentation.player.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Révision produit F40 du 2026-08-18 : un repli automatique reste actif d'un zapping à l'autre
 * jusqu'à confirmation explicite que la meilleure qualité refonctionne, ou jusqu'à expiration de
 * la fenêtre de rappel.
 */
class LiveQualityDowngradeMemoryTest {

    @Test
    fun `no memory means the top quality is probed`() {
        val memory = LiveQualityDowngradeMemory()

        assertTrue(memory.shouldProbeTop("tf1", 0))
        assertNull(memory.rememberedStreamId("tf1", 0))
    }

    @Test
    fun `a recorded downgrade is remembered on the next zap`() {
        val memory = LiveQualityDowngradeMemory()

        memory.recordDowngrade("tf1", streamId = 5, nowMs = 1_000)

        assertEquals(5, memory.rememberedStreamId("tf1", nowMs = 2_000))
        assertTrue("le repli mémorisé ne doit pas retenter le haut", !memory.shouldProbeTop("tf1", nowMs = 2_000))
    }

    @Test
    fun `confirming the top quality clears the memorized downgrade`() {
        val memory = LiveQualityDowngradeMemory()
        memory.recordDowngrade("tf1", streamId = 5, nowMs = 1_000)

        memory.confirmTopHealthy("tf1")

        assertNull(memory.rememberedStreamId("tf1", nowMs = 2_000))
        assertTrue(memory.shouldProbeTop("tf1", nowMs = 2_000))
    }

    @Test
    fun `the recall window expires and the top quality gets probed again`() {
        val memory = LiveQualityDowngradeMemory()
        memory.recordDowngrade("tf1", streamId = 5, nowMs = 0)

        assertNull(memory.rememberedStreamId("tf1", nowMs = 10 * 60 * 1000L))
        assertTrue(memory.shouldProbeTop("tf1", nowMs = 10 * 60 * 1000L))
    }

    @Test
    fun `channels are tracked independently`() {
        val memory = LiveQualityDowngradeMemory()
        memory.recordDowngrade("tf1", streamId = 5, nowMs = 0)

        assertNull(memory.rememberedStreamId("m6", nowMs = 0))
    }

    @Test
    fun `a blank link key is never recorded`() {
        val memory = LiveQualityDowngradeMemory()

        memory.recordDowngrade("", streamId = 5, nowMs = 0)

        assertNull(memory.rememberedStreamId("", nowMs = 0))
    }
}
