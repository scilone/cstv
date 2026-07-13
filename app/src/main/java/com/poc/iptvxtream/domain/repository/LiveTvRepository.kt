package com.poc.iptvxtream.domain.repository

import com.poc.iptvxtream.domain.model.LiveCategory
import com.poc.iptvxtream.domain.model.LiveEpgProgram
import com.poc.iptvxtream.domain.model.LiveStream

interface LiveTvRepository {
    suspend fun getLiveCategories(forceRefresh: Boolean): List<LiveCategory>
    suspend fun getLiveStreams(categoryId: String, forceRefresh: Boolean): List<LiveStream>
    suspend fun saveRecentlyWatched(stream: LiveStream)
    suspend fun getRecentlyWatched(): List<LiveStream>
    suspend fun getLiveEpg(streamId: Int, forceRefresh: Boolean = false): LiveEpgProgram?
}
