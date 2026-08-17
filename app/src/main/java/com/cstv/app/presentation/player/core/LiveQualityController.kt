package com.cstv.app.presentation.player.core

import com.cstv.app.domain.model.LiveVariant
import com.cstv.app.presentation.livetv.LiveQualitySession
import com.cstv.app.presentation.livetv.QualityMode
import com.cstv.app.presentation.livetv.VariantMeasurement

/** F42 will supply an archive-aware filter; until then it leaves candidates untouched. */
fun interface LiveQualityCandidateFilter { fun filter(candidates: List<LiveVariant>): List<LiveVariant> }

/** F41 will close its buffer before a source switch; no-op while F41 is absent. */
fun interface LiveQualityPreSwitchHook { fun beforeSwitch() }

/** F40's platform-independent state machine. The UI owns actual Media3 preparation. */
class LiveQualityController(
    private val candidateFilter: LiveQualityCandidateFilter = LiveQualityCandidateFilter { it },
    private val preSwitchHook: LiveQualityPreSwitchHook = LiveQualityPreSwitchHook {}
) {
    private var session: LiveQualitySession? = null
    private var generation = 0L
    private var activeStreamId: Int? = null
    private var readyAtMs: Long? = null
    private var finalRetryUsed = false

    fun start(linkKey: String, candidates: List<LiveVariant>, automatic: Boolean): LiveVariant? {
        val filtered = candidateFilter.filter(candidates)
        session = LiveQualitySession(linkKey, if (automatic && filtered.size > 1) QualityMode.AUTOMATIC else QualityMode.MANUAL, filtered)
        generation++; finalRetryUsed = false; readyAtMs = null
        return filtered.firstOrNull()?.takeIf { session?.mode == QualityMode.AUTOMATIC }?.also { activeStreamId = it.stream.streamId }
    }
    fun currentSession(): LiveQualitySession? = session
    fun generation(): Long = generation
    fun onReady(token: Long, nowMs: Long) { if (token == generation) readyAtMs = nowMs }
    /** Records the originally requested stream in a manual session without changing it. */
    fun retainManualInitial(variant: LiveVariant) {
        activeStreamId = variant.stream.streamId
    }
    fun selectManually(variant: LiveVariant): LiveVariant {
        session = session?.copy(automaticDisabledByUser = true)
        preSwitchHook.beforeSwitch()
        activeStreamId = variant.stream.streamId; readyAtMs = null; generation++; return variant
    }
    fun activeStreamId(): Int? = activeStreamId
    fun onFailure(token: Long, measurement: VariantMeasurement, nowMs: Long): LiveVariant? {
        if (token != generation || !mayAutomaticallySwitch(nowMs)) return null
        val current = activeStreamId ?: return null
        com.cstv.app.di.IptvLog.d(
            "F40",
            "quality fallback stream=${current.hashCode()} buffers=${measurement.bufferingCount} " +
                "bufferMs=${measurement.bufferingDurationMs} ready=${measurement.reachedReady}"
        )
        val updated = session ?: return null
        val nextSession = updated.copy(
            attempted = updated.attempted + current,
            measurements = updated.measurements + (current to measurement)
        )
        val next = nextSession.candidates.firstOrNull { it.stream.streamId !in nextSession.attempted }
        session = nextSession
        if (next != null) { preSwitchHook.beforeSwitch(); activeStreamId = next.stream.streamId; readyAtMs = null; generation++; return next }
        // A single bounded final retry of the objectively least-bad candidate.
        if (finalRetryUsed) return null
        finalRetryUsed = true
        val best = nextSession.candidates.minWithOrNull(compareBy<LiveVariant> { measurementFor(nextSession, it).reachedReady.not() }
            .thenBy { measurementFor(nextSession, it).bufferingCount }
            .thenBy { measurementFor(nextSession, it).bufferingDurationMs }
            .thenBy { measurementFor(nextSession, it).openingDelayMs }
            .thenByDescending { it.qualityRank })
        best?.let { preSwitchHook.beforeSwitch() }
        activeStreamId = best?.stream?.streamId; readyAtMs = null; generation++; return best
    }
    private fun mayAutomaticallySwitch(nowMs: Long): Boolean {
        val current = session ?: return false
        return current.mode == QualityMode.AUTOMATIC && !current.automaticDisabledByUser &&
            (readyAtMs == null || nowMs - readyAtMs!! >= COOLDOWN_MS)
    }
    private fun measurementFor(session: LiveQualitySession, variant: LiveVariant) = session.measurements[variant.stream.streamId] ?: VariantMeasurement()
    private companion object { const val COOLDOWN_MS = 3_000L }
}
