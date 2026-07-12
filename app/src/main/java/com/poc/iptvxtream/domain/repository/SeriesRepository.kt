package com.poc.iptvxtream.domain.repository

import com.poc.iptvxtream.domain.model.SeriesCategory
import com.poc.iptvxtream.domain.model.SeriesDetails
import com.poc.iptvxtream.domain.model.SeriesStream

interface SeriesRepository {
    suspend fun getSeriesCategories(forceRefresh: Boolean): List<SeriesCategory>
    suspend fun getSeriesStreams(categoryId: String, forceRefresh: Boolean): List<SeriesStream>
    suspend fun getSeriesDetails(seriesId: Int): SeriesDetails
    suspend fun savePlaybackPosition(episodeStreamId: Int, positionMs: Long, durationMs: Long)
    suspend fun getPlaybackPosition(episodeStreamId: Int): Pair<Long, Long>?
    suspend fun clearPlaybackPosition(episodeStreamId: Int)
}
