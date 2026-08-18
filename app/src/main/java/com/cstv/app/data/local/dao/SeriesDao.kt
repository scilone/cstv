package com.cstv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.cstv.app.data.local.entity.SeriesCategoryEntity
import com.cstv.app.data.local.entity.SeriesEpisodeEntity
import com.cstv.app.data.local.entity.SeriesSeasonEntity
import com.cstv.app.data.local.entity.SeriesStreamEntity
import com.cstv.app.domain.model.LocalSearchQuery
import kotlinx.coroutines.flow.Flow

data class RecommendableSeriesProjection(
    val seriesId: Int,
    val categoryId: String,
    val genre: String?,
    val actors: String?,
    val director: String?,
    val rating: String?,
    val added: String?,
    val releaseYear: Int?
)

@Dao
interface SeriesDao {

    // --- Recommendations ---
    @Query("SELECT seriesId, categoryId, genre, actors, director, rating, added, releaseYear FROM series_streams")
    suspend fun getRecommendableSeriesStreams(): List<RecommendableSeriesProjection>

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

    /** T21 backfill: an empty key is the durable cursor; never group it at read time. */
    @Query("SELECT * FROM series_streams WHERE linkKey = '' ORDER BY seriesId LIMIT :limit")
    suspend fun getUnnormalizedStreams(limit: Int): List<SeriesStreamEntity>

    /** Updates only T21 columns and only still-pending rows: a concurrent sync wins. */
    @Query("UPDATE series_streams SET cleanTitle = :cleanTitle, linkKey = :linkKey, languageTag = :languageTag, languageRaw = :languageRaw, qualityTag = :qualityTag, qualityRaw = :qualityRaw, versionLabel = :versionLabel WHERE seriesId = :seriesId AND linkKey = ''")
    suspend fun applyNormalization(seriesId: Int, cleanTitle: String, linkKey: String, languageTag: String?, languageRaw: String?, qualityTag: String?, qualityRaw: String?, versionLabel: String?)

    /** F39 §8.2, correction F39-R8 : voir VodDao.getStreamsByLinkKey — même tri SQL par qualité avant `LIMIT`. */
    @Query(
        "SELECT * FROM series_streams WHERE linkKey = :linkKey AND linkKey != '' " +
            "AND (:year IS NULL OR :year <= 0 OR releaseYear IS NULL OR releaseYear <= 0 OR releaseYear = :year) " +
            "ORDER BY (CASE qualityTag WHEN 'uhd_4k' THEN 40 WHEN 'fhd' THEN 30 WHEN 'hd' THEN 20 WHEN 'sd' THEN 10 ELSE 0 END) DESC, " +
            "seriesId ASC LIMIT :limit"
    )
    suspend fun getStreamsByLinkKey(linkKey: String, year: Int?, limit: Int): List<SeriesStreamEntity>

    /**
     * F39 §8.2 point 2 : épisode d'une série candidate pour le couple
     * saison/épisode courant, ou `null` si absent (série incomplète en cache
     * ou pas encore synchronisée) — jamais d'appel réseau déclenché ici.
     */
    @Query("SELECT * FROM series_episodes WHERE seriesId = :seriesId AND seasonNum = :seasonNum AND episodeNum = :episodeNum LIMIT 1")
    suspend fun getEpisodeBySeasonEpisode(seriesId: Int, seasonNum: Int, episodeNum: Int): SeriesEpisodeEntity?

    @Query("SELECT * FROM series_streams WHERE categoryId = :categoryId ORDER BY orderIndex ASC")
    fun observeStreamsByCategory(categoryId: String): Flow<List<SeriesStreamEntity>>

    // Voir VodDao : projection de liste, mêmes raisons.
    // Voir VodDao : plafond par catégorie servi par l'index couvrant.
    @Query(
        "SELECT seriesId, name, cover, rating, added, categoryId, genre, releaseYear, languageTag, qualityTag, versionLabel " +
            "FROM series_streams WHERE categoryRank < :limit ORDER BY categoryRank ASC"
    )
    fun observeAllStreamListRows(limit: Int): Flow<List<SeriesStreamListRow>>

    @Query(
        "SELECT seriesId, name, cover, rating, added, categoryId, genre, releaseYear, languageTag, qualityTag, versionLabel " +
            "FROM series_streams WHERE categoryId = :categoryId ORDER BY orderIndex ASC"
    )
    fun observeStreamListRowsByCategory(categoryId: String): Flow<List<SeriesStreamListRow>>

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

    /**
     * Ne jamais appeler directement : contourne le calcul de [SeriesStreamEntity.searchText].
     *
     * T20 : `@Upsert`, pas `@Insert(REPLACE)` — `series_seasons`/`series_episodes` portent
     * désormais une cascade vers `seriesId` (§4.2) ; `REPLACE` la déclencherait à chaque sync.
     */
    @Upsert
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

    /** T20 §4.3 : différentiel horodaté — voir `LiveTvDao.replaceAllStreams`, même raison
     *  (`series_seasons`/`series_episodes` en cascade sur `seriesId`). */
    @Transaction
    suspend fun replaceAllStreams(streams: List<SeriesStreamEntity>) {
        if (streams.isEmpty()) return
        val batchTs = streams.first().cachedAt
        streams.chunked(VodDao.INSERT_CHUNK_SIZE).forEach { insertStreams(it) }
        deleteStreamsCachedBefore(batchTs)
    }

    @Transaction
    suspend fun replaceStreamsByCategory(categoryId: String, streams: List<SeriesStreamEntity>) {
        if (streams.isEmpty()) return
        val batchTs = streams.first().cachedAt
        streams.chunked(VodDao.INSERT_CHUNK_SIZE).forEach { insertStreams(it) }
        deleteStreamsCachedBefore(batchTs, categoryId)
    }

    @Query("DELETE FROM series_streams WHERE cachedAt < :batchTs")
    suspend fun deleteStreamsCachedBefore(batchTs: Long)

    @Query("DELETE FROM series_streams WHERE cachedAt < :batchTs AND categoryId = :categoryId")
    suspend fun deleteStreamsCachedBefore(batchTs: Long, categoryId: String)

    @Query("SELECT * FROM series_streams WHERE seriesId = :seriesId LIMIT 1")
    suspend fun getStreamById(seriesId: Int): SeriesStreamEntity?

    @Query("SELECT * FROM series_streams WHERE seriesId IN (:seriesIds)")
    suspend fun getStreamsByIds(seriesIds: List<Int>): List<SeriesStreamEntity>

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
