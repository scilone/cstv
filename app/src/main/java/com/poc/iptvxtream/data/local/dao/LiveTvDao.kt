package com.poc.iptvxtream.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.poc.iptvxtream.data.local.entity.EpgCacheEntity
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

    @Query("SELECT * FROM live_streams ORDER BY num ASC")
    suspend fun getAllStreams(): List<LiveStreamEntity>

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

    // --- FTS4 (Phase 40 : recherche globale) --- voir VodDao pour le détail.
    @Query("INSERT OR REPLACE INTO live_streams_fts(rowid, name, categoryId) VALUES (:streamId, :name, :categoryId)")
    suspend fun upsertLiveFts(streamId: Int, name: String, categoryId: String)

    @Query("DELETE FROM live_streams_fts WHERE categoryId = :categoryId")
    suspend fun clearFtsByCategory(categoryId: String)

    @Query("DELETE FROM live_streams_fts")
    suspend fun clearAllFts()

    @Transaction
    suspend fun insertStreamsWithFts(streams: List<LiveStreamEntity>) {
        insertStreams(streams)
        streams.forEach { upsertLiveFts(it.streamId, it.name, it.categoryId) }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentlyWatched(recentlyWatched: RecentlyWatchedLiveEntity)

    @Query("SELECT * FROM recently_watched_live WHERE profileId = :profileId ORDER BY watchedAt DESC LIMIT :limit")
    suspend fun getRecentlyWatched(profileId: Int, limit: Int): List<RecentlyWatchedLiveEntity>

    @Query("DELETE FROM recently_watched_live WHERE profileId = :profileId")
    suspend fun deleteRecentlyWatchedForProfile(profileId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpgCache(epg: EpgCacheEntity)

    @Query("SELECT * FROM epg_cache WHERE streamId = :streamId LIMIT 1")
    suspend fun getEpgCache(streamId: Int): EpgCacheEntity?

    @Query("DELETE FROM epg_cache WHERE streamId = :streamId")
    suspend fun deleteEpgCache(streamId: Int)

    @Query("DELETE FROM epg_cache")
    suspend fun clearEpgCache()
}
