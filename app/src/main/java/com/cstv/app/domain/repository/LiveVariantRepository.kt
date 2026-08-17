package com.cstv.app.domain.repository

import com.cstv.app.domain.model.LiveVariant

/** Resolves a live stream's T21 group from the local catalogue. */
interface LiveVariantRepository {
    suspend fun variantsFor(streamId: Int): List<LiveVariant>
}
