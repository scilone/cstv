package com.cstv.app.domain.model

/** F40: a deterministic, locally resolved live-stream candidate. */
data class LiveVariant(
    val stream: LiveStream,
    val qualityRank: Int = mediaQualityRank(stream.qualityTag)
)

fun LiveVariant.displayQuality(fallback: String): String =
    stream.qualityRaw?.takeIf { it.isNotBlank() }
        ?: stream.qualityTag?.uppercase()?.replace('_', ' ')
        ?: fallback
