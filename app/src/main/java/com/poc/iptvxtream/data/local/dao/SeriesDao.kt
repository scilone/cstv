package com.poc.iptvxtream.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    // Compteurs du sélecteur de catégorie (basés sur le cache local).
    @Query("SELECT categoryId, COUNT(*) AS count FROM series_streams GROUP BY categoryId")
    suspend fun getCategoryCounts(): List<CategoryCount>

    @Query("SELECT * FROM series_streams WHERE categoryId = :categoryId ORDER BY name ASC")
    suspend fun getStreamsByCategory(categoryId: String): List<SeriesStreamEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStreams(streams: List<SeriesStreamEntity>)

    @Query("DELETE FROM series_streams WHERE categoryId = :categoryId")
    suspend fun clearStreamsByCategory(categoryId: String)

    @Query("DELETE FROM series_streams")
    suspend fun clearAllStreams()

    // --- FTS4 (Phase 40 : recherche globale) --- voir VodDao pour le détail.
    @Query(
        "INSERT OR REPLACE INTO series_streams_fts(rowid, name, actors, director, genre, categoryId) " +
            "VALUES (:seriesId, :name, :actors, :director, :genre, :categoryId)"
    )
    suspend fun upsertSeriesFts(seriesId: Int, name: String, actors: String?, director: String?, genre: String?, categoryId: String)

    @Query("DELETE FROM series_streams_fts WHERE categoryId = :categoryId")
    suspend fun clearFtsByCategory(categoryId: String)

    @Query("DELETE FROM series_streams_fts")
    suspend fun clearAllFts()

    @Transaction
    suspend fun insertStreamsWithFts(streams: List<SeriesStreamEntity>) {
        insertStreams(streams)
        streams.forEach { upsertSeriesFts(it.seriesId, it.name, it.actors, it.director, it.genre, it.categoryId) }
    }

    @Query("SELECT * FROM series_streams WHERE seriesId = :seriesId LIMIT 1")
    suspend fun getStreamById(seriesId: Int): SeriesStreamEntity?

    @Query("SELECT * FROM series_streams WHERE actors IS NULL OR director IS NULL OR genre IS NULL LIMIT :limit")
    suspend fun getStreamsNeedingEnrichment(limit: Int): List<SeriesStreamEntity>
}
