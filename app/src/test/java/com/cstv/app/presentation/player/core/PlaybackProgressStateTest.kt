package com.cstv.app.presentation.player.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackProgressStateTest {

    @Test
    fun `hides a saved position while duration is unknown`() {
        val state = playbackProgressState(positionMs = 120_000L, durationMs = 0L)

        assertFalse(state.isReady)
        assertEquals(0L, state.positionMs)
        assertEquals(0L, state.durationMs)
        assertEquals(1f, state.sliderRangeEnd)
        assertEquals(1f, state.sliderRangeEnd)
    }

    @Test
    fun `hides progress when duration is negative`() {
        val state = playbackProgressState(positionMs = 5_000L, durationMs = -1L)

        assertFalse(state.isReady)
        assertEquals(0L, state.positionMs)
        assertEquals(0L, state.durationMs)
    }

    @Test
    fun `clamps invalid positions when duration is known`() {
        val beforeStart = playbackProgressState(positionMs = -1L, durationMs = 10_000L)
        val afterEnd = playbackProgressState(positionMs = 15_000L, durationMs = 10_000L)

        assertTrue(beforeStart.isReady)
        assertEquals(0L, beforeStart.positionMs)
        assertEquals(10_000L, afterEnd.positionMs)
        assertEquals(10_000L, afterEnd.durationMs)

        val atEnd = playbackProgressState(positionMs = 10_000L, durationMs = 10_000L)
        assertEquals(10_000L, atEnd.positionMs)
    }

    @Test
    fun `preserves valid playback progress`() {
        val state = playbackProgressState(positionMs = 2_500L, durationMs = 10_000L)

        assertTrue(state.isReady)
        assertEquals(2_500L, state.positionMs)
        assertEquals(10_000L, state.durationMs)
        assertEquals(10_000f, state.sliderRangeEnd)
    }
}
