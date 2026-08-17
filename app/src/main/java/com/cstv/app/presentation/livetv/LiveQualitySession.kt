package com.cstv.app.presentation.livetv

import com.cstv.app.domain.model.LiveVariant

enum class QualityMode { MANUAL, AUTOMATIC }

data class VariantMeasurement(
    val reachedReady: Boolean = false,
    val bufferingCount: Int = 0,
    val bufferingDurationMs: Long = 0L,
    val openingDelayMs: Long = Long.MAX_VALUE
)

/** Ephemeral F40 state. It is intentionally recreated for every zap. */
data class LiveQualitySession(
    val linkKey: String,
    val mode: QualityMode,
    val candidates: List<LiveVariant>,
    val attempted: Set<Int> = emptySet(),
    val measurements: Map<Int, VariantMeasurement> = emptyMap(),
    val automaticDisabledByUser: Boolean = false
)
