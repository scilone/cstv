package com.poc.iptvxtream.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.poc.iptvxtream.data.local.entity.PlaybackPositionEntity
import com.poc.iptvxtream.data.local.entity.VodCategoryEntity
import com.poc.iptvxtream.data.local.entity.VodStreamEntity

@Dao
interface VodDao {

    // --- Categories ---
    @Query("SELECT * FROM vod_categories ORDER BY orderIndex ASC")
    suspend fun getAllCategories(): List<VodCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<VodCategoryEntity>)

    @Query("DELETE FROM vod_categories")
    suspend fun clearCategories()

    // --- Streams ---
    @Query("SELECT * FROM vod_streams WHERE categoryId = :categoryId ORDER BY name ASC")
    suspend fun getStreamsByCategory(categoryId: String): List<VodStreamEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStreams(streams: List<VodStreamEntity>)

    @Query("DELETE FROM vod_streams WHERE categoryId = :categoryId")
    suspend fun clearStreamsByCategory(categoryId: String)

    @Query("DELETE FROM vod_streams")
    suspend fun clearAllStreams()

    // --- Playback Positions (Resume) ---
    @Query("SELECT * FROM playback_positions ORDER BY lastAccessedAt DESC")
    suspend fun getAllPlaybackPositions(): List<PlaybackPositionEntity>

    @Query("SELECT * FROM playback_positions WHERE streamId = :streamId LIMIT 1")
    suspend fun getPlaybackPosition(streamId: Int): PlaybackPositionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlaybackPosition(position: PlaybackPositionEntity)

    @Query("DELETE FROM playback_positions WHERE streamId = :streamId")
    suspend fun deletePlaybackPosition(streamId: Int)
}
