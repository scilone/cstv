package com.cstv.app.presentation.player.core

import com.cstv.app.presentation.livetv.VariantMeasurement

/** Small injectable clock so F40's time window is deterministic on the JVM. */
fun interface MonotonicClock { fun nowMs(): Long }

/** F40 §8.3: observes state transitions only; product decisions stay in the controller. */
class LiveStabilityMonitor(private val clock: MonotonicClock) {
    private val interruptions = ArrayDeque<Long>()
    private var preparedAtMs = clock.nowMs()
    private var bufferingStartedAtMs: Long? = null
    private var bufferingDurationMs = 0L
    private var reachedReady = false
    private var openingDelayMs = Long.MAX_VALUE
    private var lastBufferEventMs: Long? = null
    private var thresholdConsumed = false

    fun reset() {
        interruptions.clear(); preparedAtMs = clock.nowMs(); bufferingStartedAtMs = null
        bufferingDurationMs = 0L; reachedReady = false; openingDelayMs = Long.MAX_VALUE
        lastBufferEventMs = null; thresholdConsumed = false
    }
    fun onReady() {
        if (!reachedReady) openingDelayMs = (clock.nowMs() - preparedAtMs).coerceAtLeast(0L)
        reachedReady = true
    }
    fun onBufferingStarted(): Boolean {
        if (!reachedReady) return false
        val now = clock.nowMs()
        if (lastBufferEventMs?.let { now - it < MERGE_MS } == true) return false
        lastBufferEventMs = now
        bufferingStartedAtMs = now
        interruptions.addLast(now)
        trim(now)
        return interruptions.size >= BUFFERING_THRESHOLD && !thresholdConsumed.also { if (!it) thresholdConsumed = true }
    }
    fun onBufferingEnded() {
        bufferingStartedAtMs?.let { bufferingDurationMs += (clock.nowMs() - it).coerceAtLeast(0L) }
        bufferingStartedAtMs = null
        trim(clock.nowMs())
    }
    fun interruptionCount(): Int { trim(clock.nowMs()); return interruptions.size }
    fun measurement(): VariantMeasurement = VariantMeasurement(
        reachedReady = reachedReady,
        bufferingCount = interruptionCount(),
        bufferingDurationMs = bufferingDurationMs + (bufferingStartedAtMs?.let { (clock.nowMs() - it).coerceAtLeast(0L) } ?: 0L),
        openingDelayMs = openingDelayMs
    )
    private fun trim(now: Long) { while (interruptions.firstOrNull()?.let { now - it >= WINDOW_MS } == true) interruptions.removeFirst() }
    private companion object { const val WINDOW_MS = 120_000L; const val MERGE_MS = 500L; const val BUFFERING_THRESHOLD = 5 }
}
