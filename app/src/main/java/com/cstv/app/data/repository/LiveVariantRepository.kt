package com.cstv.app.data.repository

import com.cstv.app.data.local.dao.LiveTvDao
import com.cstv.app.domain.model.LiveStream
import com.cstv.app.domain.model.LiveVariant
import com.cstv.app.domain.model.mediaQualityRank
import com.cstv.app.domain.repository.LiveVariantRepository as LiveVariantRepositoryContract
import javax.inject.Inject
import javax.inject.Singleton

/** F40 §8.1: local-only resolver; an empty T21 key is never a group. */
@Singleton
class LiveVariantRepository @Inject constructor(private val liveTvDao: LiveTvDao) : LiveVariantRepositoryContract {
    override suspend fun variantsFor(streamId: Int): List<LiveVariant> {
        val linkKey = liveTvDao.getStreamById(streamId)?.linkKey?.takeIf { it.isNotBlank() } ?: return emptyList()
        return liveTvDao.getStreamsByLinkKey(linkKey)
            .map { it.toDomain() }
            .sortedWith(compareByDescending<LiveStream> { mediaQualityRank(it.qualityTag) }
                .thenBy { it.num.takeIf { number -> number > 0 } ?: Int.MAX_VALUE }
                .thenBy { it.streamId })
            .take(MAX_VARIANTS)
            .map(::LiveVariant)
    }

    private companion object { const val MAX_VARIANTS = 20 }
}
