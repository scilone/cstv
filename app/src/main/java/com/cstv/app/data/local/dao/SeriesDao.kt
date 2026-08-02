package com.cstv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.cstv.app.data.local.entity.SeriesCategoryEntity
import com.cstv.app.data.local.entity.SeriesEpisodeEntity
import com.cstv.app.data.local.entity.SeriesSeasonEntity
import com.cstv.app.data.local.entity.SeriesStreamEntity
import com.cstv.app.domain.model.LocalSearchQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface SeriesDao {

    // --- Categories ---
    @Query("SELECT * FROM series_categories ORDER BY orderIndex ASC")
    suspend fun getAllCategories(): List<SeriesCategoryEntity>

    @Query("SELECT * FROM series_categories ORDER BY orderIndex ASC")
    fun observeAllCategories(): Flow<List<SeriesCategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<SeriesCategoryEntity>)

    @Query("DELETE FROM series_categories")
    suspend fun clearCategories()

    // --- Streams ---
    @Query("SELECT * FROM series_streams ORDER BY orderIndex ASC")
    suspend fun getAllStreams(): List<SeriesStreamEntity>

    @Query("SELECT * FROM series_streams ORDER BY orderIndex ASC")
    fun observeAllStreams(): Flow<List<SeriesStreamEntity>>

    @Query("SELECT * FROM series_streams ORDER BY orderIndex ASC")
    fun getAllStreamsPaged(): androidx.paging.PagingSource<Int, SeriesStreamEntity>

    // Compteurs du sélecteur de catégorie (basés sur le cache local).
    @Query("SELECT categoryId, COUNT(*) AS count FROM series_streams GROUP BY categoryId")
    suspend fun getCategoryCounts(): List<CategoryCount>

    @Query("SELECT EXISTS(SELECT 1 FROM series_streams)")
    suspend fun hasStreams(): Boolean

    @Query("SELECT * FROM series_streams WHERE categoryId = :categoryId ORDER BY orderIndex ASC")
    suspend fun getStreamsByCategory(categoryId: String): List<SeriesStreamEntity>

    // Appariement TMDB : voir VodDao.getStreamsByReleaseYearPage (année exacte
    // pour les séries enrichies, année lue dans le titre pour les autres, page
    // obligatoire pour rester sous le CursorWindow de 2 Mo).
    @Query(
        "SELECT * FROM series_streams " +
            "WHERE releaseYear = :year " +
            "OR ((releaseYear IS NULL OR releaseYear <= 0) AND name LIKE :yearPattern) " +
            "ORDER BY orderIndex ASC LIMIT :limit OFFSET :offset"
    )
    suspend fun getStreamsByReleaseYearPage(
        year: Int,
        yearPattern: String,
        limit: Int,
        offset: Int
    ): List<SeriesStreamEntity>

    @Query("SELECT * FROM series_streams WHERE categoryId = :categoryId ORDER BY orderIndex ASC")
    fun observeStreamsByCategory(categoryId: String): Flow<List<SeriesStreamEntity>>

    @Query("SELECT * FROM series_streams WHERE categoryId = :categoryId ORDER BY orderIndex ASC")
    fun getStreamsByCategoryPaged(categoryId: String): androidx.paging.PagingSource<Int, SeriesStreamEntity>

    @Query("DELETE FROM series_streams WHERE categoryId = :categoryId")
    suspend fun clearStreamsByCategory(categoryId: String)

    @Query("DELETE FROM series_streams")
    suspend fun clearAllStreams()

    @Transaction
    suspend fun insertStreams(streams: List<SeriesStreamEntity>) {
        insertStreamsRaw(streams.map {
            it.copy(searchText = LocalSearchQuery.buildCatalogSearchText(it.name, it.actors, it.director, it.genre, it.categoryId))
        })
    }

    /** Ne jamais appeler directement : contourne le calcul de [SeriesStreamEntity.searchText]. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStreamsRaw(streams: List<SeriesStreamEntity>)

    /**
     * Une réponse vide ne remplace jamais un catalogue connu : une erreur de
     * panel ne doit pas transformer le cache local en catalogue vide.
     */
    @Transaction
    suspend fun replaceAllCategories(categories: List<SeriesCategoryEntity>) {
        if (categories.isEmpty()) return
        clearCategories()
        insertCategories(categories)
    }

    @Transaction
    suspend fun replaceAllStreams(streams: List<SeriesStreamEntity>) {
        if (streams.isEmpty()) return
        clearAllStreams()
        streams.chunked(VodDao.INSERT_CHUNK_SIZE).forEach { insertStreams(it) }
    }

    @Transaction
    suspend fun replaceStreamsByCategory(categoryId: String, streams: List<SeriesStreamEntity>) {
        if (streams.isEmpty()) return
        clearStreamsByCategory(categoryId)
        streams.chunked(VodDao.INSERT_CHUNK_SIZE).forEach { insertStreams(it) }
    }

    @Query("SELECT * FROM series_streams WHERE seriesId = :seriesId LIMIT 1")
    suspend fun getStreamById(seriesId: Int): SeriesStreamEntity?

    @Query("SELECT categoryId FROM series_streams WHERE seriesId = :seriesId LIMIT 1")
    suspend fun getCategoryIdForSeries(seriesId: Int): String?

    // --- Saisons et épisodes (T4 : fiche série hors ligne) ---
    // Peuplés à la consultation d'une fiche en ligne, jamais par balayage.
    @Query("SELECT * FROM series_seasons WHERE seriesId = :seriesId ORDER BY seasonNumber ASC")
    suspend fun getSeasons(seriesId: Int): List<SeriesSeasonEntity>

    @Query("SELECT * FROM series_episodes WHERE seriesId = :seriesId ORDER BY seasonNum ASC, orderIndex ASC")
    suspend fun getEpisodes(seriesId: Int): List<SeriesEpisodeEntity>

    @Query("SELECT * FROM series_episodes WHERE episodeId = :episodeId LIMIT 1")
    suspend fun getEpisodeById(episodeId: Int): SeriesEpisodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeasons(seasons: List<SeriesSeasonEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(episodes: List<SeriesEpisodeEntity>)

    @Query("DELETE FROM series_seasons WHERE seriesId = :seriesId")
    suspend fun clearSeasons(seriesId: Int)

    @Query("DELETE FROM series_episodes WHERE seriesId = :seriesId")
    suspend fun clearEpisodes(seriesId: Int)

    @Query("DELETE FROM series_seasons")
    suspend fun clearAllSeasons()

    @Query("DELETE FROM series_episodes")
    suspend fun clearAllEpisodes()

    /**
     * Remplace le détail d'une seule série. Une liste d'épisodes vide n'efface
     * rien : `get_series_info` peut répondre sans épisodes sur un panel fautif.
     */
    @Transaction
    suspend fun replaceSeriesDetail(
        seriesId: Int,
        seasons: List<SeriesSeasonEntity>,
        episodes: List<SeriesEpisodeEntity>
    ) {
        if (episodes.isEmpty()) return
        clearSeasons(seriesId)
        clearEpisodes(seriesId)
        insertSeasons(seasons)
        episodes.chunked(VodDao.INSERT_CHUNK_SIZE).forEach { insertEpisodes(it) }
    }

    @Query("SELECT * FROM series_streams WHERE actors IS NULL OR director IS NULL OR genre IS NULL OR releaseYear IS NULL LIMIT :limit")
    suspend fun getStreamsNeedingEnrichment(limit: Int): List<SeriesStreamEntity>

    // Préfiltre SQL des « titres associés » (Phase 60) : voir VodDao.getStreamsByGenre.
    @Query("SELECT * FROM series_streams WHERE genre LIKE :pattern")
    suspend fun getStreamsByGenre(pattern: String): List<SeriesStreamEntity>

    // Bornes dynamiques du filtre "année de sortie" (Recherche avancée) : voir
    // VodDao.getMinReleaseYear/getMaxReleaseYear (0 = sentinelle exclue).
    @Query("SELECT MIN(releaseYear) FROM series_streams WHERE releaseYear IS NOT NULL AND releaseYear > 0")
    suspend fun getMinReleaseYear(): Int?

    @Query("SELECT MAX(releaseYear) FROM series_streams WHERE releaseYear IS NOT NULL AND releaseYear > 0")
    suspend fun getMaxReleaseYear(): Int?
}
