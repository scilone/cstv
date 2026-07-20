package com.cstv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.cstv.app.data.local.entity.PlaybackPositionEntity
import com.cstv.app.data.local.entity.VodCategoryEntity
import com.cstv.app.data.local.entity.VodStreamEntity
import kotlinx.coroutines.flow.Flow

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
    @Query("SELECT * FROM vod_streams ORDER BY orderIndex ASC")
    suspend fun getAllStreams(): List<VodStreamEntity>

    @Query("SELECT * FROM vod_streams ORDER BY orderIndex ASC")
    fun getAllStreamsPaged(): androidx.paging.PagingSource<Int, VodStreamEntity>

    // Compteurs du sélecteur de catégorie (basés sur le cache local).
    @Query("SELECT categoryId, COUNT(*) AS count FROM vod_streams GROUP BY categoryId")
    suspend fun getCategoryCounts(): List<CategoryCount>

    @Query("SELECT * FROM vod_streams WHERE categoryId = :categoryId ORDER BY orderIndex ASC")
    suspend fun getStreamsByCategory(categoryId: String): List<VodStreamEntity>

    @Query("SELECT * FROM vod_streams WHERE categoryId = :categoryId ORDER BY orderIndex ASC")
    fun getStreamsByCategoryPaged(categoryId: String): androidx.paging.PagingSource<Int, VodStreamEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStreams(streams: List<VodStreamEntity>)

    @Query("SELECT * FROM vod_streams WHERE streamId = :streamId LIMIT 1")
    suspend fun getStreamById(streamId: Int): VodStreamEntity?

    @Query("DELETE FROM vod_streams WHERE categoryId = :categoryId")
    suspend fun clearStreamsByCategory(categoryId: String)

    @Query("DELETE FROM vod_streams")
    suspend fun clearAllStreams()

    // --- FTS4 (Phase 40 : recherche globale) ---
    // Table virtuelle déclarée en @Entity (VodStreamFtsEntity), pilotée ici
    // par des @Query brutes (pas d'@Insert/@Delete générés). rowid de
    // vod_streams_fts = streamId (INTEGER PRIMARY KEY = alias de rowid en
    // SQLite), pour joindre sans colonne dédiée.
    @Query(
        "INSERT OR REPLACE INTO vod_streams_fts(rowid, name, actors, director, genre, categoryId) " +
            "VALUES (:streamId, :name, :actors, :director, :genre, :categoryId)"
    )
    suspend fun upsertVodFts(streamId: Int, name: String, actors: String?, director: String?, genre: String?, categoryId: String)

    @Query("DELETE FROM vod_streams_fts WHERE categoryId = :categoryId")
    suspend fun clearFtsByCategory(categoryId: String)

    @Query("DELETE FROM vod_streams_fts")
    suspend fun clearAllFts()

    @Transaction
    suspend fun insertStreamsWithFts(streams: List<VodStreamEntity>) {
        insertStreams(streams)
        streams.forEach { upsertVodFts(it.streamId, it.name, it.actors, it.director, it.genre, it.categoryId) }
    }

    @Query("SELECT * FROM vod_streams WHERE actors IS NULL OR director IS NULL OR genre IS NULL OR releaseYear IS NULL LIMIT :limit")
    suspend fun getStreamsNeedingEnrichment(limit: Int): List<VodStreamEntity>

    // Préfiltre SQL des « titres associés » (Phase 60) : candidats dont le genre
    // brut contient le motif LIKE. Le match exact token-à-token (évite le
    // sur-match "War" dans "Warrior") est fait ensuite côté domaine.
    @Query("SELECT * FROM vod_streams WHERE genre LIKE :pattern")
    suspend fun getStreamsByGenre(pattern: String): List<VodStreamEntity>

    // Bornes dynamiques du filtre "année de sortie" (Recherche avancée) :
    // releaseYear = 0 est la sentinelle "vérifié mais année inconnue" (voir
    // ReleaseYearParser), exclue ici comme dans le mapping domain (0 -> null).
    @Query("SELECT MIN(releaseYear) FROM vod_streams WHERE releaseYear IS NOT NULL AND releaseYear > 0")
    suspend fun getMinReleaseYear(): Int?

    @Query("SELECT MAX(releaseYear) FROM vod_streams WHERE releaseYear IS NOT NULL AND releaseYear > 0")
    suspend fun getMaxReleaseYear(): Int?

    // --- Playback Positions (Resume) ---
    @Query("SELECT * FROM playback_positions WHERE profileId = :profileId ORDER BY lastAccessedAt DESC")
    suspend fun getAllPlaybackPositions(profileId: Int): List<PlaybackPositionEntity>

    // Phase 41 : ré-émet automatiquement à chaque écriture (savePlaybackPosition/
    // deletePlaybackPosition), sans reload manuel depuis les ViewModels.
    @Query("SELECT * FROM playback_positions WHERE profileId = :profileId ORDER BY lastAccessedAt DESC")
    fun observeAllPlaybackPositions(profileId: Int): Flow<List<PlaybackPositionEntity>>

    @Query("SELECT * FROM playback_positions WHERE streamId = :streamId AND profileId = :profileId LIMIT 1")
    suspend fun getPlaybackPosition(streamId: Int, profileId: Int): PlaybackPositionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlaybackPosition(position: PlaybackPositionEntity)

    @Query("DELETE FROM playback_positions WHERE streamId = :streamId AND profileId = :profileId")
    suspend fun deletePlaybackPosition(streamId: Int, profileId: Int)

    @Query("DELETE FROM playback_positions WHERE profileId = :profileId")
    suspend fun deleteAllPlaybackForProfile(profileId: Int)
}
