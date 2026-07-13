package com.poc.iptvxtream.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.poc.iptvxtream.data.local.entity.SeriesCategoryEntity
import com.poc.iptvxtream.data.local.entity.SeriesStreamEntity

@Dao
interface SeriesDao {

    // --- Categories ---
    @Query("SELECT * FROM series_categories ORDER BY orderIndex ASC")
    suspend fun getAllCategories(): List<SeriesCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<SeriesCategoryEntity>)

    @Query("DELETE FROM series_categories")
    suspend fun clearCategories()

    // --- Streams ---
    @Query("SELECT * FROM series_streams ORDER BY name ASC")
    suspend fun getAllStreams(): List<SeriesStreamEntity>

    @Query("SELECT * FROM series_streams WHERE categoryId = :categoryId ORDER BY name ASC")
    suspend fun getStreamsByCategory(categoryId: String): List<SeriesStreamEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStreams(streams: List<SeriesStreamEntity>)

    @Query("DELETE FROM series_streams WHERE categoryId = :categoryId")
    suspend fun clearStreamsByCategory(categoryId: String)

    @Query("DELETE FROM series_streams")
    suspend fun clearAllStreams()

    @Query("SELECT * FROM series_streams WHERE seriesId = :seriesId LIMIT 1")
    suspend fun getStreamById(seriesId: Int): SeriesStreamEntity?

    @Query("SELECT * FROM series_streams WHERE actors IS NULL OR director IS NULL OR genre IS NULL")
    suspend fun getStreamsNeedingEnrichment(): List<SeriesStreamEntity>
}
