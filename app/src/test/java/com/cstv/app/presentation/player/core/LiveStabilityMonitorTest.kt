package com.cstv.app.presentation.player.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveStabilityMonitorTest {
    private var now = 0L
    private val monitor = LiveStabilityMonitor { now }

    @Test fun `initial buffering and duplicate callbacks do not count`() {
        assertFalse(monitor.onBufferingStarted())
        monitor.onReady()
        assertFalse(monitor.onBufferingStarted())
        now += 100
        assertFalse(monitor.onBufferingStarted())
        assertEquals(1, monitor.interruptionCount())
    }

    @Test fun `fifth interruption in two minutes triggers recovery`() {
        monitor.onReady()
        repeat(4) { assertFalse(monitor.onBufferingStarted()); monitor.onBufferingEnded(); now += 1_000 }
        assertTrue(monitor.onBufferingStarted())
    }

    @Test fun `old interruptions leave the sliding window`() {
        monitor.onReady()
        repeat(4) { monitor.onBufferingStarted(); monitor.onBufferingEnded(); now += 1_000 }
        now += 120_000
        assertFalse(monitor.onBufferingStarted())
        assertEquals(1, monitor.interruptionCount())
    }

    @Test fun `measurement includes ready opening delay and cumulative buffering duration`() {
        now = 250
        monitor.onReady()
        now = 1_000
        monitor.onBufferingStarted()
        now = 1_700
        monitor.onBufferingEnded()
        val measurement = monitor.measurement()
        assertTrue(measurement.reachedReady)
        assertEquals(250, measurement.openingDelayMs)
        assertEquals(700, measurement.bufferingDurationMs)
    }
}
