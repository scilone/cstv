package com.cstv.app.domain.repository

import com.cstv.app.domain.model.LiveStream
import com.cstv.app.domain.model.PlaybackPosition
import kotlinx.coroutines.flow.Flow

/** Local, profile-scoped viewing history. */
interface ViewingHistoryRepository {
    fun observeRecentlyWatched(limit: Int = 10): Flow<List<LiveStream>>
    suspend fun removeFromContinueWatching(position: PlaybackPosition)
    suspend fun removeRecentlyWatched(streamId: Int)
}
