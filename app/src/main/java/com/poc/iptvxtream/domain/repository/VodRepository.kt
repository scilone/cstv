package com.poc.iptvxtream.domain.repository

import com.poc.iptvxtream.domain.model.PlaybackPosition
import com.poc.iptvxtream.domain.model.VodCategory
import com.poc.iptvxtream.domain.model.VodDetails
import com.poc.iptvxtream.domain.model.VodStream

interface VodRepository {
    suspend fun getVodCategories(forceRefresh: Boolean): List<VodCategory>
    suspend fun getVodStreams(categoryId: String, forceRefresh: Boolean): List<VodStream>
    suspend fun getVodDetails(streamId: Int): VodDetails
    suspend fun savePlaybackPosition(
        streamId: Int,
        positionMs: Long,
        durationMs: Long,
        title: String? = null,
        coverUrl: String? = null,
        type: String? = null,
        containerExtension: String? = null,
        seriesId: Int? = null,
        episodeNum: Int? = null,
        seasonNum: Int? = null,
        plot: String? = null,
        duration: String? = null,
        releaseDate: String? = null
    )
    suspend fun getPlaybackPosition(streamId: Int): Pair<Long, Long>?
    suspend fun clearPlaybackPosition(streamId: Int)
    suspend fun getAllPlaybackPositions(): List<PlaybackPosition>
}
