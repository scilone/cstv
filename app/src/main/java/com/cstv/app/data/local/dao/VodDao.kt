package com.cstv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.cstv.app.data.local.entity.PlaybackPositionEntity
import com.cstv.app.data.local.entity.VodCategoryEntity
import com.cstv.app.data.local.entity.VodStreamEntity
import com.cstv.app.domain.model.LocalSearchQuery
import kotlinx.coroutines.flow.Flow

/** T20: display projection for "Continuer à regarder" — resume state joined to the current
 *  catalogue (`vod_streams` for movies, `series_episodes` + `series_streams` for episodes). */
data class PlaybackListRow(
    val providerId: Int,
    val kind: String,
    val positionMs: Long,
    val durationMs: Long,
    val lastAccessedAt: Long,
    val title: String?,
    val coverUrl: String?,
    val containerExtension: String?,
    val seriesId: Int?,
    val episodeNum: Int?,
    val seasonNum: Int?,
    val plot: String?,
    val duration: String?,
    val releaseDate: String?,
    val categoryId: String?,
    /** Genre du catalogue joint : le lecteur l'affiche sur une reprise, où aucun
     *  appel `get_vod_info` n'a eu lieu. Vient de la série pour un épisode. */
    val genre: String?,
)

data class PlaybackPositionValues(val positionMs: Long, val durationMs: Long)

/** T20 (§4.6): lean cloud sync projection -- no catalogue join, unlike [PlaybackListRow]. A
 *  position syncs fine even for a media not yet present in the local catalogue. */
data class PlaybackWireRow(val providerId: Int, val kind: String, val positionMs: Long, val durationMs: Long, val lastAccessedAt: Long)

internal const val PLAYBACK_LIST_QUERY = """
    SELECT r.providerId AS providerId, r.kind AS kind, pp.positionMs AS positionMs, pp.durationMs AS durationMs,
           pp.lastAccessedAt AS lastAccessedAt, s.name AS title, s.streamIcon AS coverUrl, s.containerExtension AS containerExtension,
           NULL AS seriesId, NULL AS episodeNum, NULL AS seasonNum, s.plot AS plot, s.duration AS duration,
           CAST(s.releaseYear AS TEXT) AS releaseDate, s.categoryId AS categoryId, s.genre AS genre
      FROM playback_positions pp JOIN media_refs r ON r.mediaUid = pp.mediaUid
      JOIN vod_streams s ON s.streamId = r.providerId
     WHERE pp.profileId = :profileId AND r.accountKey = :accountKey AND r.kind = 'movie'
    UNION ALL
    SELECT r.providerId, r.kind, pp.positionMs, pp.durationMs, pp.lastAccessedAt,
           e.title, e.movieImage, e.containerExtension,
           e.seriesId, e.episodeNum, e.seasonNum, e.plot, e.duration, e.releaseDate, ss.categoryId,
           ss.genre
      FROM playback_positions pp JOIN media_refs r ON r.mediaUid = pp.mediaUid
      JOIN series_episodes e ON e.episodeId = r.providerId
      JOIN series_streams ss ON ss.seriesId = e.seriesId
     WHERE pp.profileId = :profileId AND r.accountKey = :accountKey AND r.kind = 'episode'
     ORDER BY lastAccessedAt DESC
"""

data class RecommendableVodProjection(
    val streamId: Int,
    val categoryId: String,
    val genre: String?,
    val actors: String?,
    val director: String?,
    val rating: String?,
    val added: String?,
    val releaseYear: Int?
)

@Dao
interface VodDao {

    // --- Recommendations ---
    @Query("""
        SELECT streamId, categoryId, genre, actors, director, rating, added, releaseYear 
        FROM vod_streams 
        WHERE categoryId NOT IN (
            SELECT cr.providerCategoryId 
            FROM category_preferences cp
            JOIN category_refs cr ON cp.catUid = cr.catUid
            WHERE cp.profileId = :profileId 
              AND cr.accountKey = :accountKey 
              AND cr.kind = 'vod' 
              AND cp.hidden = 1
        )
    """)
    suspend fun getRecommendableVodStreams(profileId: Int, accountKey: String): List<RecommendableVodProjection>

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

    /** T21 backfill: an empty key is the durable cursor; never group it at read time. */
    @Query("SELECT * FROM vod_streams WHERE linkKey = '' ORDER BY streamId LIMIT :limit")
    suspend fun getUnnormalizedStreams(limit: Int): List<VodStreamEntity>

    /** Updates only T21 columns and only still-pending rows: a concurrent sync wins. */
    @Query("UPDATE vod_streams SET cleanTitle = :cleanTitle, linkKey = :linkKey, languageTag = :languageTag, languageRaw = :languageRaw, qualityTag = :qualityTag, qualityRaw = :qualityRaw, versionLabel = :versionLabel WHERE streamId = :streamId AND linkKey = ''")
    suspend fun applyNormalization(streamId: Int, cleanTitle: String, linkKey: String, languageTag: String?, languageRaw: String?, qualityTag: String?, qualityRaw: String?, versionLabel: String?)

    /**
     * F39 §8.2, correction F39-R8 : autres versions d'une œuvre partageant
     * `linkKey`, filtrées par année compatible (une entrée sans année connue
     * reste candidate). Le rang qualité n'étant pas une colonne, il est
     * recalculé en SQL par un `CASE` répliquant exactement `mediaQualityRank`
     * (`MediaQuality`) — nécessaire pour trier **avant** `LIMIT` : sur un
     * groupe anormalement large, la meilleure qualité peut se trouver après
     * le vingtième `streamId`, et un tri Kotlin appliqué après coup sur des
     * lignes déjà tronquées ne la retrouve jamais. Le repository trie encore
     * ce résultat côté Kotlin (ordre stable, sécurité de non-régression) mais
     * ce tri SQL garantit que les meilleures candidates font partie des
     * lignes renvoyées. `limit` applique le plafond défensif §8.7 ; le
     * repository interroge `limit + 1` pour distinguer une troncature réelle
     * d'un groupe qui tombe pile sur le plafond.
     */
    @Query(
        "SELECT * FROM vod_streams WHERE linkKey = :linkKey AND linkKey != '' " +
            "AND (:year IS NULL OR :year <= 0 OR releaseYear IS NULL OR releaseYear <= 0 OR releaseYear = :year) " +
            "ORDER BY (CASE qualityTag WHEN 'uhd_4k' THEN 40 WHEN 'fhd' THEN 30 WHEN 'hd' THEN 20 WHEN 'sd' THEN 10 ELSE 0 END) DESC, " +
            "streamId ASC LIMIT :limit"
    )
    suspend fun getStreamsByLinkKey(linkKey: String, year: Int?, limit: Int): List<VodStreamEntity>

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
        "SELECT streamId, name, streamIcon, rating, added, categoryId, genre, releaseYear, languageTag, qualityTag, versionLabel " +
            "FROM vod_streams WHERE categoryRank < :limit ORDER BY categoryRank ASC"
    )
    fun observeAllStreamListRows(limit: Int): Flow<List<VodStreamListRow>>

    @Query(
        "SELECT streamId, name, streamIcon, rating, added, categoryId, genre, releaseYear, languageTag, qualityTag, versionLabel " +
            "FROM vod_streams WHERE categoryId = :categoryId ORDER BY orderIndex ASC"
    )
    fun observeStreamListRowsByCategory(categoryId: String): Flow<List<VodStreamListRow>>

    @Query("SELECT * FROM vod_streams WHERE categoryId = :categoryId ORDER BY orderIndex ASC")
    fun getStreamsByCategoryPaged(categoryId: String): androidx.paging.PagingSource<Int, VodStreamEntity>

    @Query("SELECT * FROM vod_streams WHERE streamId = :streamId LIMIT 1")
    suspend fun getStreamById(streamId: Int): VodStreamEntity?

    @Query("SELECT * FROM vod_streams WHERE streamId IN (:streamIds)")
    suspend fun getStreamsByIds(streamIds: List<Int>): List<VodStreamEntity>

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

    /**
     * Ne jamais appeler directement : contourne le calcul de [VodStreamEntity.searchText].
     *
     * T20-R4 : `@Upsert` (`INSERT … ON CONFLICT DO UPDATE`), pas `@Insert(REPLACE)`. `REPLACE`
     * compile en `INSERT OR REPLACE`, qui supprime la ligne en conflit avant de la réinsérer et
     * déclencherait `ON DELETE CASCADE` sur tout enfant du catalogue VOD à chaque synchronisation —
     * même piège que `LiveTvDao`/`SeriesDao`, voir `CatalogUpsertSqlTest` et T20 §4.3.
     */
    @Upsert
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

    /**
     * T20-R4 : différentiel horodaté, pas purge totale — voir `LiveTvDao.replaceAllStreams`
     * pour le motif complet. Chaque appelant stampe son lot avec le même `cachedAt`
     * (`VodRepositoryImpl.syncVodStreams`, déjà le cas) ; upsert du lot puis suppression des
     * seules lignes non revues laisse les films maintenus intacts — la cascade (téléchargements,
     * reprises via `media_refs`, etc.) ne se déclenche que pour un film réellement disparu.
     */
    @Transaction
    suspend fun replaceAllStreams(streams: List<VodStreamEntity>) {
        if (streams.isEmpty()) return
        val batchTs = streams.first().cachedAt
        streams.chunked(INSERT_CHUNK_SIZE).forEach { insertStreams(it) }
        deleteStreamsCachedBefore(batchTs)
    }

    @Transaction
    suspend fun replaceStreamsByCategory(categoryId: String, streams: List<VodStreamEntity>) {
        if (streams.isEmpty()) return
        val batchTs = streams.first().cachedAt
        streams.chunked(INSERT_CHUNK_SIZE).forEach { insertStreams(it) }
        deleteStreamsCachedBefore(batchTs, categoryId)
    }

    @Query("DELETE FROM vod_streams WHERE cachedAt < :batchTs")
    suspend fun deleteStreamsCachedBefore(batchTs: Long)

    @Query("DELETE FROM vod_streams WHERE cachedAt < :batchTs AND categoryId = :categoryId")
    suspend fun deleteStreamsCachedBefore(batchTs: Long, categoryId: String)

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

    // --- Playback Positions (Resume) : T20, pure state joined to the current catalogue ---

    @Query(PLAYBACK_LIST_QUERY)
    suspend fun getAllPlaybackPositions(profileId: Int, accountKey: String): List<PlaybackListRow>

    // Phase 41 : ré-émet automatiquement à chaque écriture (savePlaybackPosition/
    // deletePlaybackPosition) ou changement du catalogue joint, sans reload manuel.
    @Query(PLAYBACK_LIST_QUERY)
    fun observeAllPlaybackPositions(profileId: Int, accountKey: String): Flow<List<PlaybackListRow>>

    @Query(
        "SELECT pp.positionMs AS positionMs, pp.durationMs AS durationMs FROM playback_positions pp " +
            "JOIN media_refs r ON r.mediaUid = pp.mediaUid " +
            "WHERE pp.profileId = :profileId AND r.accountKey = :accountKey AND r.kind = :kind AND r.providerId = :providerId LIMIT 1"
    )
    suspend fun getPlaybackPosition(profileId: Int, accountKey: String, kind: String, providerId: Int): PlaybackPositionValues?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlaybackPosition(position: PlaybackPositionEntity)

    @Query(
        "DELETE FROM playback_positions WHERE profileId = :profileId AND mediaUid = (" +
            "SELECT mediaUid FROM media_refs WHERE accountKey = :accountKey AND kind = :kind AND providerId = :providerId LIMIT 1)"
    )
    suspend fun deletePlaybackPosition(profileId: Int, accountKey: String, kind: String, providerId: Int)

    @Query("DELETE FROM playback_positions WHERE profileId = :profileId")
    suspend fun deleteAllPlaybackForProfile(profileId: Int)

    /** T19-R2: persistent equivalent of the snapshot-time playback cap — see
     *  [FavoritesDao.pruneToMostRecent] for why this must delete in Room, not just at read time. */
    @Query(
        "DELETE FROM playback_positions WHERE profileId = :profileId AND rowid NOT IN (" +
            "SELECT rowid FROM playback_positions WHERE profileId = :profileId ORDER BY lastAccessedAt DESC LIMIT :limit)"
    )
    suspend fun prunePlaybackToMostRecent(profileId: Int, limit: Int)

    /** Toutes les positions d'épisode d'une série (retrait forcé après note « je n'aime pas »). */
    @Query(
        "DELETE FROM playback_positions WHERE profileId = :profileId AND mediaUid IN (" +
            "SELECT r.mediaUid FROM media_refs r JOIN series_episodes e ON e.episodeId = r.providerId " +
            "WHERE r.accountKey = :accountKey AND r.kind = 'episode' AND e.seriesId = :seriesId)"
    )
    suspend fun deletePlaybackPositionsBySeriesId(profileId: Int, accountKey: String, seriesId: Int)

    @Query(
        "DELETE FROM playback_positions WHERE profileId = :profileId AND mediaUid IN (" +
            "SELECT mediaUid FROM media_refs WHERE accountKey = :accountKey AND kind = 'episode' AND providerId IN (:episodeIds))"
    )
    suspend fun deletePlaybackPositionsByStreamIds(profileId: Int, accountKey: String, episodeIds: Set<Int>)

    /** Séries présentes dans « Continuer à regarder » du profil (F12 : séries éligibles à la détection de nouveaux épisodes). */
    @Query(
        "SELECT DISTINCT e.seriesId FROM playback_positions pp " +
            "JOIN media_refs r ON r.mediaUid = pp.mediaUid " +
            "JOIN series_episodes e ON e.episodeId = r.providerId " +
            "WHERE pp.profileId = :profileId AND r.accountKey = :accountKey AND r.kind = 'episode'"
    )
    suspend fun getWatchedSeriesIds(profileId: Int, accountKey: String): List<Int>

    /** T20 (§4.6): cloud sync projection, no catalogue metadata. */
    @Query(
        "SELECT r.providerId AS providerId, r.kind AS kind, pp.positionMs AS positionMs, pp.durationMs AS durationMs, pp.lastAccessedAt AS lastAccessedAt " +
            "FROM playback_positions pp JOIN media_refs r ON r.mediaUid = pp.mediaUid " +
            "WHERE pp.profileId = :profileId AND r.accountKey = :accountKey"
    )
    suspend fun wireRows(profileId: Int, accountKey: String): List<PlaybackWireRow>

    companion object {
        /** Borne le pic mémoire d'une transaction sur un catalogue de dizaines de milliers d'entrées. */
        const val INSERT_CHUNK_SIZE = 500
    }
}
