package com.poc.iptvxtream.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.poc.iptvxtream.data.local.entity.LiveCategoryEntity
import com.poc.iptvxtream.data.local.entity.LiveStreamEntity
import com.poc.iptvxtream.data.local.entity.RecentlyWatchedLiveEntity

@Dao
interface LiveTvDao {

    @Query("SELECT * FROM live_categories ORDER BY orderIndex ASC")
    suspend fun getAllCategories(): List<LiveCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<LiveCategoryEntity>)

    @Query("DELETE FROM live_categories")
    suspend fun clearCategories()

    @Query("SELECT * FROM live_streams WHERE categoryId = :categoryId ORDER BY num ASC")
    suspend fun getStreamsByCategory(categoryId: String): List<LiveStreamEntity>

    @Query("SELECT * FROM live_streams WHERE streamId = :streamId LIMIT 1")
    suspend fun getStreamById(streamId: Int): LiveStreamEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStreams(streams: List<LiveStreamEntity>)

    @Query("DELETE FROM live_streams WHERE categoryId = :categoryId")
    suspend fun clearStreamsByCategory(categoryId: String)

    @Query("DELETE FROM live_streams")
    suspend fun clearAllStreams()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentlyWatched(recentlyWatched: RecentlyWatchedLiveEntity)

    @Query("SELECT * FROM recently_watched_live ORDER BY watchedAt DESC LIMIT :limit")
    suspend fun getRecentlyWatched(limit: Int): List<RecentlyWatchedLiveEntity>
}
