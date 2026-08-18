package com.cstv.app.presentation.player.core

import com.cstv.app.domain.model.LiveStream
import com.cstv.app.domain.model.LiveVariant
import com.cstv.app.presentation.livetv.VariantMeasurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveQualityControllerTest {
    private fun variant(id: Int, rank: Int = 10) = LiveVariant(LiveStream(id, "C$id", null, null, id, "1"), rank)

    @Test fun `automatic mode tries candidates once in declared order`() {
        val controller = LiveQualityController()
        assertEquals(1, controller.start("news", listOf(variant(1), variant(2), variant(3)), automatic = true)?.stream?.streamId)
        assertEquals(2, controller.onFailure(controller.generation(), VariantMeasurement(), 4_000)?.stream?.streamId)
        assertEquals(3, controller.onFailure(controller.generation(), VariantMeasurement(), 8_000)?.stream?.streamId)
        assertTrue(controller.currentSession()!!.attempted.containsAll(setOf(1, 2)))
    }

    @Test fun `manual selection stops automatic fallback for this session`() {
        val controller = LiveQualityController()
        controller.start("news", listOf(variant(1), variant(2)), automatic = true)
        controller.selectManually(variant(2))
        assertNull(controller.onFailure(controller.generation(), VariantMeasurement(), 4_000))
        assertTrue(controller.currentSession()!!.automaticDisabledByUser)
    }

    @Test fun `manual mode keeps the requested stream while automatic opens the best candidate`() {
        val candidates = listOf(variant(1), variant(2))
        val manual = LiveQualityController()
        assertNull(manual.start("news", candidates, automatic = false))
        manual.retainManualInitial(candidates[1])
        assertEquals(2, manual.activeStreamId())
        assertEquals(1, LiveQualityController().start("news", candidates, automatic = true)?.stream?.streamId)
    }

    @Test fun `cooldown after ready refuses an immediate automatic fallback`() {
        val controller = LiveQualityController()
        controller.start("news", listOf(variant(1), variant(2)), automatic = true)
        val token = controller.generation()
        controller.onReady(token, 1_000)
        assertNull(controller.onFailure(token, VariantMeasurement(reachedReady = true), 3_999))
        assertEquals(2, controller.onFailure(token, VariantMeasurement(reachedReady = true), 4_000)?.stream?.streamId)
    }

    @Test fun `stale callback is ignored`() {
        val controller = LiveQualityController()
        controller.start("news", listOf(variant(1), variant(2)), automatic = true)
        val stale = controller.generation()
        controller.selectManually(variant(2))
        assertNull(controller.onFailure(stale, VariantMeasurement(), 4_000))
    }

    @Test fun `exhaustion retries least bad candidate once`() {
        val controller = LiveQualityController()
        controller.start("news", listOf(variant(1, 20), variant(2, 10)), automatic = true)
        controller.onFailure(controller.generation(), VariantMeasurement(reachedReady = false, bufferingCount = 1), 4_000)
        val best = controller.onFailure(controller.generation(), VariantMeasurement(reachedReady = true, bufferingCount = 5), 8_000)
        assertEquals(2, best?.stream?.streamId)
        assertNull(controller.onFailure(controller.generation(), VariantMeasurement(), 12_000))
    }

    @Test fun `retour utilisateur 2026-08-18 - preferredStreamId starts below the top candidate`() {
        val controller = LiveQualityController()

        val started = controller.start("news", listOf(variant(1), variant(2), variant(3)), automatic = true, preferredStreamId = 2)

        assertEquals(2, started?.stream?.streamId)
        assertEquals(2, controller.activeStreamId())
    }

    @Test fun `retour utilisateur 2026-08-18 - a failure below a preferred start never climbs back to the skipped top`() {
        val controller = LiveQualityController()
        controller.start("news", listOf(variant(1), variant(2), variant(3)), automatic = true, preferredStreamId = 2)

        val next = controller.onFailure(controller.generation(), VariantMeasurement(), 4_000)

        assertEquals("descend vers 3, ne remonte jamais vers 1 (sauté volontairement)", 3, next?.stream?.streamId)
    }

    @Test fun `retour utilisateur 2026-08-18 - an unknown preferredStreamId falls back to the top candidate`() {
        val controller = LiveQualityController()

        val started = controller.start("news", listOf(variant(1), variant(2)), automatic = true, preferredStreamId = 999)

        assertEquals(1, started?.stream?.streamId)
    }

    @Test fun `future F41 and F42 hooks are callable without a hard dependency`() {
        var purges = 0
        val controller = LiveQualityController(
            candidateFilter = LiveQualityCandidateFilter { candidates -> candidates.filter { it.stream.streamId != 1 } },
            preSwitchHook = LiveQualityPreSwitchHook { purges++ }
        )
        assertEquals(2, controller.start("news", listOf(variant(1), variant(2), variant(3)), automatic = true)?.stream?.streamId)
        controller.selectManually(variant(3))
        assertEquals(1, purges)
    }
}
