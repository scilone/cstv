package com.cstv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.cstv.app.data.local.entity.PlaybackPositionEntity
import com.cstv.app.data.local.entity.VodCategoryEntity
import com.cstv.app.data.local.entity.VodStreamEntity
import com.cstv.app.domain.model.LocalSearchQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface VodDao {

    // --- Categories ---
    @Query("SELECT * FROM vod_categories ORDER BY orderIndex ASC")
    suspend fun getAllCategories(): List<VodCategoryEntity>

    @Query("SELECT * FROM vod_categories ORDER BY orderIndex ASC")
    fun observeAllCategories(): Flow<List<VodCategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<VodCategoryEntity>)

    @Query("DELETE FROM vod_categories")
    suspend fun clearCategories()

    // --- Streams ---
    @Query("SELECT * FROM vod_streams ORDER BY orderIndex ASC")
    suspend fun getAllStreams(): List<VodStreamEntity>

    @Query("SELECT * FROM vod_streams ORDER BY orderIndex ASC")
    fun observeAllStreams(): Flow<List<VodStreamEntity>>

    @Query("SELECT * FROM vod_streams ORDER BY orderIndex ASC")
    fun getAllStreamsPaged(): androidx.paging.PagingSource<Int, VodStreamEntity>

    // Compteurs du sélecteur de catégorie (basés sur le cache local).
    @Query("SELECT categoryId, COUNT(*) AS count FROM vod_streams GROUP BY categoryId")
    suspend fun getCategoryCounts(): List<CategoryCount>

    @Query("SELECT EXISTS(SELECT 1 FROM vod_streams)")
    suspend fun hasStreams(): Boolean

    @Query("SELECT * FROM vod_streams WHERE categoryId = :categoryId ORDER BY orderIndex ASC")
    suspend fun getStreamsByCategory(categoryId: String): List<VodStreamEntity>

    // Appariement TMDB, une année à la fois (voir
    // VodRepositoryImpl.getCachedVodStreamsByYears) : les films déjà enrichis
    // sont pris sur `releaseYear`, les autres sur la présence de l'année dans le
    // titre — TmdbCatalogMatcher.yearFromTitle sait la lire là ("Odyssée (2016)
    // 1080p", "Odyssée 2016", "Odyssee.2016.MULTI"), donc le motif est un simple
    // `%2016%` sans délimiteur : tout format plus étroit rejetterait des titres
    // que le matcher, lui, accepte.
    //
    // La page est obligatoire : charger toutes les lignes d'un coup dépassait le
    // CursorWindow de 2 Mo d'Android et levait « Couldn't read row N from
    // CursorWindow » sur un gros catalogue.
    @Query(
        "SELECT * FROM vod_streams " +
            "WHERE releaseYear = :year " +
            "OR ((releaseYear IS NULL OR releaseYear <= 0) AND name LIKE :yearPattern) " +
            "ORDER BY orderIndex ASC LIMIT :limit OFFSET :offset"
    )
    suspend fun getStreamsByReleaseYearPage(
        year: Int,
        yearPattern: String,
        limit: Int,
        offset: Int
    ): List<VodStreamEntity>

    @Query("SELECT * FROM vod_streams WHERE categoryId = :categoryId ORDER BY orderIndex ASC")
    fun observeStreamsByCategory(categoryId: String): Flow<List<VodStreamEntity>>

    // Projection de liste : voir VodStreamListRow pour le motif (fenêtre de
    // curseur et tri temporaire). Ces deux requêtes remplacent les variantes
    // `SELECT *` pour les écrans de catalogue.
    // Onglet « Tout » : plafonné à `limit` films par catégorie, puisque les
    // rangées horizontales n'en affichent pas davantage et que le total réel
    // est déjà servi par `getCategoryCounts`.
    //
    // `WHERE categoryRank < :limit ORDER BY categoryRank ASC` attaque l'index
    // couvrant par sa colonne de tête : SQLite s'y positionne, balaie jusqu'au
    // rang `limit` et s'arrête, sans jamais toucher la table ni construire de
    // tri temporaire. Le tri par `categoryRank` seul suffit à l'affichage :
    // l'ordre des rangées vient de la liste des catégories, et `groupBy`
    // (LinkedHashMap) conserve l'ordre de rencontre à l'intérieur de chaque
    // groupe — donc les rangs croissants de la catégorie.
    @Query(
        "SELECT streamId, name, streamIcon, rating, added, categoryId, genre, releaseYear " +
            "FROM vod_streams WHERE categoryRank < :limit ORDER BY categoryRank ASC"
    )
    fun observeAllStreamListRows(limit: Int): Flow<List<VodStreamListRow>>

    @Query(
        "SELECT streamId, name, streamIcon, rating, added, categoryId, genre, releaseYear " +
            "FROM vod_streams WHERE categoryId = :categoryId ORDER BY orderIndex ASC"
    )
    fun observeStreamListRowsByCategory(categoryId: String): Flow<List<VodStreamListRow>>

    @Query("SELECT * FROM vod_streams WHERE categoryId = :categoryId ORDER BY orderIndex ASC")
    fun getStreamsByCategoryPaged(categoryId: String): androidx.paging.PagingSource<Int, VodStreamEntity>

    @Query("SELECT * FROM vod_streams WHERE streamId = :streamId LIMIT 1")
    suspend fun getStreamById(streamId: Int): VodStreamEntity?

    @Query("SELECT categoryId FROM vod_streams WHERE streamId = :streamId LIMIT 1")
    suspend fun getCategoryIdForStream(streamId: Int): String?

    @Query("DELETE FROM vod_streams WHERE categoryId = :categoryId")
    suspend fun clearStreamsByCategory(categoryId: String)

    @Query("DELETE FROM vod_streams")
    suspend fun clearAllStreams()

    @Transaction
    suspend fun insertStreams(streams: List<VodStreamEntity>) {
        insertStreamsRaw(streams.map {
            it.copy(searchText = LocalSearchQuery.buildCatalogSearchText(it.name, it.actors, it.director, it.genre, it.categoryId))
        })
    }

    /** Ne jamais appeler directement : contourne le calcul de [VodStreamEntity.searchText]. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStreamsRaw(streams: List<VodStreamEntity>)

    /**
     * Une réponse vide ne remplace jamais un catalogue connu : une erreur de
     * panel ne doit pas transformer le cache local en catalogue vide.
     */
    @Transaction
    suspend fun replaceAllCategories(categories: List<VodCategoryEntity>) {
        if (categories.isEmpty()) return
        clearCategories()
        insertCategories(categories)
    }

    @Transaction
    suspend fun replaceAllStreams(streams: List<VodStreamEntity>) {
        if (streams.isEmpty()) return
        clearAllStreams()
        streams.chunked(INSERT_CHUNK_SIZE).forEach { insertStreams(it) }
    }

    @Transaction
    suspend fun replaceStreamsByCategory(categoryId: String, streams: List<VodStreamEntity>) {
        if (streams.isEmpty()) return
        clearStreamsByCategory(categoryId)
        streams.chunked(INSERT_CHUNK_SIZE).forEach { insertStreams(it) }
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

    /** T19-R2: persistent equivalent of the snapshot-time playback cap — see
     *  [FavoritesDao.pruneToMostRecent] for why this must delete in Room, not just at read time. */
    @Query(
        "DELETE FROM playback_positions WHERE profileId = :profileId AND rowid NOT IN (" +
            "SELECT rowid FROM playback_positions WHERE profileId = :profileId ORDER BY lastAccessedAt DESC LIMIT :limit)"
    )
    suspend fun prunePlaybackToMostRecent(profileId: Int, limit: Int)

    @Query("DELETE FROM playback_positions WHERE seriesId = :seriesId AND profileId = :profileId")
    suspend fun deletePlaybackPositionsBySeriesId(seriesId: Int, profileId: Int)

    @Query("DELETE FROM playback_positions WHERE streamId IN (:streamIds) AND profileId = :profileId")
    suspend fun deletePlaybackPositionsByStreamIds(streamIds: Set<Int>, profileId: Int)

    /** Séries présentes dans « Continuer à regarder » du profil (F12 : séries éligibles à la détection de nouveaux épisodes). */
    @Query("SELECT DISTINCT seriesId FROM playback_positions WHERE profileId = :profileId AND seriesId IS NOT NULL")
    suspend fun getWatchedSeriesIds(profileId: Int): List<Int>

    companion object {
        /** Borne le pic mémoire d'une transaction sur un catalogue de dizaines de milliers d'entrées. */
        const val INSERT_CHUNK_SIZE = 500
    }
}
