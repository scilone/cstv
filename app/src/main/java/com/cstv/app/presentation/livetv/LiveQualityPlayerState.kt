package com.cstv.app.presentation.livetv

import com.cstv.app.domain.model.LiveStream
import com.cstv.app.domain.model.LiveVariant
import com.cstv.app.domain.repository.LiveVariantRepository

data class LiveQualityPlayerState(
    val variants: List<LiveVariant>,
    val selectedStream: LiveStream,
    val generation: Long
)

/** Keeps legacy JVM ViewModel construction non-null without making production wiring optional. */
object EmptyLiveVariantRepository : LiveVariantRepository {
    override suspend fun variantsFor(streamId: Int): List<LiveVariant> = emptyList()
}
