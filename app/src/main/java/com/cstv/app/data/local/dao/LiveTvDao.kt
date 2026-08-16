package com.cstv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.cstv.app.data.local.entity.EpgCacheEntity
import com.cstv.app.data.local.entity.LiveCategoryEntity
import com.cstv.app.data.local.entity.LiveStreamEntity
import com.cstv.app.data.local.entity.RecentlyWatchedLiveEntity
import com.cstv.app.domain.model.LocalSearchQuery
import kotlinx.coroutines.flow.Flow

/** T20: display projection — recently-watched joined to the current live catalogue. */
data class RecentlyWatchedListRow(
    val providerId: Int,
    val watchedAt: Long,
    val name: String,
    val streamIcon: String?,
    val categoryId: String?,
    val num: Int?,
)

data class RecentlyWatchedWireRow(val providerId: Int, val watchedAt: Long)

internal const val RECENTLY_WATCHED_LIST_QUERY = """
    SELECT r.providerId AS providerId, rw.watchedAt AS watchedAt, s.name AS name, s.streamIcon AS streamIcon,
           s.categoryId AS categoryId, s.num AS num
      FROM recently_watched_live rw JOIN media_refs r ON r.mediaUid = rw.mediaUid
      JOIN live_streams s ON s.streamId = r.providerId
     WHERE rw.profileId = :profileId AND r.accountKey = :accountKey
     ORDER BY watchedAt DESC LIMIT :limit
"""

@Dao
interface LiveTvDao {

    @Query("SELECT * FROM live_categories ORDER BY orderIndex ASC")
    suspend fun getAllCategories(): List<LiveCategoryEntity>

    @Query("SELECT * FROM live_categories ORDER BY orderIndex ASC")
    fun observeAllCategories(): Flow<List<LiveCategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<LiveCategoryEntity>)

    @Query("DELETE FROM live_categories")
    suspend fun clearCategories()

    @Query("SELECT * FROM live_streams ORDER BY num ASC")
    suspend fun getAllStreams(): List<LiveStreamEntity>

    @Query("SELECT * FROM live_streams ORDER BY num ASC")
    fun observeAllStreams(): Flow<List<LiveStreamEntity>>

    // Projection de liste : voir LiveStreamListRow. Ces deux requêtes
    // remplacent les variantes `SELECT *` pour les écrans de catalogue.
    //
    // Onglet « Tout » : plafonné à `limit` chaînes par catégorie, servi par
    // l'index couvrant dont `categoryRank` est la colonne de tête — ni accès
    // table, ni tri temporaire. Voir `VodDao.observeAllStreamListRows` pour le
    // détail du motif ; `categoryRank` classe ici les chaînes par `num`, qui est
    // l'ordre d'affichage.
    @Query(
        "SELECT streamId, name, streamIcon, epgChannelId, num, categoryId " +
            "FROM live_streams WHERE categoryRank < :limit ORDER BY categoryRank ASC"
    )
    fun observeAllStreamListRows(limit: Int): Flow<List<LiveStreamListRow>>

    @Query(
        "SELECT streamId, name, streamIcon, epgChannelId, num, categoryId " +
            "FROM live_streams WHERE categoryId = :categoryId ORDER BY num ASC"
    )
    fun observeStreamListRowsByCategory(categoryId: String): Flow<List<LiveStreamListRow>>

    @Query("SELECT * FROM live_streams ORDER BY num ASC")
    fun getAllStreamsPaged(): androidx.paging.PagingSource<Int, LiveStreamEntity>

    // Compteurs du sélecteur de catégorie (basés sur le cache local).
    @Query("SELECT categoryId, COUNT(*) AS count FROM live_streams GROUP BY categoryId")
    suspend fun getCategoryCounts(): List<CategoryCount>

    @Query("SELECT * FROM live_streams WHERE categoryId = :categoryId ORDER BY num ASC")
    suspend fun getStreamsByCategory(categoryId: String): List<LiveStreamEntity>

    @Query("SELECT * FROM live_streams WHERE categoryId = :categoryId ORDER BY num ASC")
    fun observeStreamsByCategory(categoryId: String): Flow<List<LiveStreamEntity>>

    @Query("SELECT * FROM live_streams WHERE categoryId = :categoryId ORDER BY num ASC")
    fun getStreamsByCategoryPaged(categoryId: String): androidx.paging.PagingSource<Int, LiveStreamEntity>

    @Query("SELECT * FROM live_streams WHERE streamId = :streamId LIMIT 1")
    suspend fun getStreamById(streamId: Int): LiveStreamEntity?

    /** T21 backfill: an empty key is the durable cursor; never group it at read time. */
    @Query("SELECT * FROM live_streams WHERE linkKey = '' ORDER BY streamId LIMIT :limit")
    suspend fun getUnnormalizedStreams(limit: Int): List<LiveStreamEntity>

    /** Updates only T21 columns and only still-pending rows: a concurrent sync wins. */
    @Query("UPDATE live_streams SET cleanTitle = :cleanTitle, linkKey = :linkKey, languageTag = :languageTag, languageRaw = :languageRaw, qualityTag = :qualityTag, qualityRaw = :qualityRaw WHERE streamId = :streamId AND linkKey = ''")
    suspend fun applyNormalization(streamId: Int, cleanTitle: String, linkKey: String, languageTag: String?, languageRaw: String?, qualityTag: String?, qualityRaw: String?)

    @Query("SELECT * FROM live_streams WHERE linkKey = :linkKey AND linkKey != '' ORDER BY num ASC")
    suspend fun getStreamsByLinkKey(linkKey: String): List<LiveStreamEntity>

    @Query("DELETE FROM live_streams WHERE categoryId = :categoryId")
    suspend fun clearStreamsByCategory(categoryId: String)

    @Query("DELETE FROM live_streams")
    suspend fun clearAllStreams()

    @Transaction
    suspend fun insertStreams(streams: List<LiveStreamEntity>) {
        insertStreamsRaw(streams.map {
            it.copy(searchText = LocalSearchQuery.buildLiveSearchText(it.name, it.categoryId))
        })
    }

    /**
     * Ne jamais appeler directement : contourne le calcul de [LiveStreamEntity.searchText].
     *
     * T20 : `@Upsert` (`INSERT … ON CONFLICT DO UPDATE`), pas `@Insert(REPLACE)`. `REPLACE`
     * compile en `INSERT OR REPLACE`, qui supprime la ligne en conflit avant de la réinsérer et
     * déclencherait donc `ON DELETE CASCADE` sur `epg_cache` à chaque synchronisation — voir
     * `CatalogUpsertSqlTest` et T20 §4.3.
     */
    @Upsert
    suspend fun insertStreamsRaw(streams: List<LiveStreamEntity>)

    /**
     * Une réponse vide ne remplace jamais un catalogue connu : une erreur de
     * panel ne doit pas transformer le cache local en catalogue vide.
     */
    @Transaction
    suspend fun replaceAllCategories(categories: List<LiveCategoryEntity>) {
        if (categories.isEmpty()) return
        clearCategories()
        insertCategories(categories)
    }

    /**
     * T20 §4.3 : différentiel horodaté, pas purge totale. `epg_cache` porte désormais une clé
     * étrangère en cascade vers `streamId` (§4.2) : un `clearAllStreams()` préalable la viderait
     * intégralement à chaque synchronisation. Chaque appelant stampe son lot avec le même
     * `cachedAt` (déjà le cas dans `LiveTvRepositoryImpl.syncLiveStreams`) ; upsert du lot puis
     * suppression des seules lignes non revues laisse les chaînes maintenues, et leur EPG, intactes
     * — la cascade ne se déclenche que pour une chaîne réellement disparue du bouquet.
     */
    @Transaction
    suspend fun replaceAllStreams(streams: List<LiveStreamEntity>) {
        if (streams.isEmpty()) return
        val batchTs = streams.first().cachedAt
        streams.chunked(VodDao.INSERT_CHUNK_SIZE).forEach { insertStreams(it) }
        deleteStreamsCachedBefore(batchTs)
    }

    @Transaction
    suspend fun replaceStreamsByCategory(categoryId: String, streams: List<LiveStreamEntity>) {
        if (streams.isEmpty()) return
        val batchTs = streams.first().cachedAt
        streams.chunked(VodDao.INSERT_CHUNK_SIZE).forEach { insertStreams(it) }
        deleteStreamsCachedBefore(batchTs, categoryId)
    }

    @Query("DELETE FROM live_streams WHERE cachedAt < :batchTs")
    suspend fun deleteStreamsCachedBefore(batchTs: Long)

    @Query("DELETE FROM live_streams WHERE cachedAt < :batchTs AND categoryId = :categoryId")
    suspend fun deleteStreamsCachedBefore(batchTs: Long, categoryId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentlyWatched(recentlyWatched: RecentlyWatchedLiveEntity)

    @Query(RECENTLY_WATCHED_LIST_QUERY)
    suspend fun getRecentlyWatched(profileId: Int, accountKey: String, limit: Int): List<RecentlyWatchedListRow>

    @Query(RECENTLY_WATCHED_LIST_QUERY)
    fun observeRecentlyWatched(profileId: Int, accountKey: String, limit: Int): Flow<List<RecentlyWatchedListRow>>

    @Query(
        "DELETE FROM recently_watched_live WHERE profileId = :profileId AND mediaUid = (" +
            "SELECT mediaUid FROM media_refs WHERE accountKey = :accountKey AND kind = 'live' AND providerId = :streamId LIMIT 1)"
    )
    suspend fun deleteRecentlyWatched(profileId: Int, accountKey: String, streamId: Int)

    @Query("DELETE FROM recently_watched_live WHERE profileId = :profileId")
    suspend fun deleteRecentlyWatchedForProfile(profileId: Int)

    /** T19-R2: persistent equivalent of the snapshot-time recently-watched cap — the read-side
     *  `LIMIT` in [getRecentlyWatched] bounds what gets pushed, but does not stop an older row from
     *  re-entering the top N once a more recent one is deleted. See [FavoritesDao.pruneToMostRecent]. */
    @Query(
        "DELETE FROM recently_watched_live WHERE profileId = :profileId AND rowid NOT IN (" +
            "SELECT rowid FROM recently_watched_live WHERE profileId = :profileId ORDER BY watchedAt DESC LIMIT :limit)"
    )
    suspend fun pruneRecentlyWatchedToMostRecent(profileId: Int, limit: Int)

    /** T20 (§4.6): cloud sync reads a projection — providerId + watchedAt only, no catalogue metadata. */
    @Query(
        "SELECT r.providerId AS providerId, rw.watchedAt AS watchedAt FROM recently_watched_live rw " +
            "JOIN media_refs r ON r.mediaUid = rw.mediaUid " +
            "WHERE rw.profileId = :profileId AND r.accountKey = :accountKey"
    )
    suspend fun wireRows(profileId: Int, accountKey: String): List<RecentlyWatchedWireRow>

    // --- EPG (T4 : fenêtre par chaîne, consultable hors ligne) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpgEntries(entries: List<EpgCacheEntity>)

    @Query("SELECT * FROM epg_cache WHERE streamId = :streamId ORDER BY startTimestamp ASC")
    suspend fun getEpgWindow(streamId: Int): List<EpgCacheEntity>

    /**
     * Programme couvrant [now]. Hors ligne, il est servi quel que soit son âge :
     * c'est [EpgCacheEntity.cachedAt] qui informe l'utilisateur, pas un filtre.
     */
    @Query(
        "SELECT * FROM epg_cache WHERE streamId = :streamId AND startTimestamp <= :now " +
            "AND endTimestamp > :now ORDER BY startTimestamp DESC LIMIT 1"
    )
    suspend fun getEpgNow(streamId: Int, now: Long): EpgCacheEntity?

    @Query(
        "SELECT * FROM epg_cache WHERE streamId = :streamId AND startTimestamp > :now " +
            "ORDER BY startTimestamp ASC LIMIT 1"
    )
    suspend fun getEpgNext(streamId: Int, now: Long): EpgCacheEntity?

    @Query("SELECT MAX(cachedAt) FROM epg_cache WHERE streamId = :streamId")
    suspend fun getEpgCachedAt(streamId: Int): Long?

    @Query("DELETE FROM epg_cache WHERE streamId = :streamId")
    suspend fun deleteEpgCache(streamId: Int)

    /**
     * Remplace la fenêtre d'une chaîne. Une réponse vide laisse la fenêtre
     * précédente en place plutôt que de supprimer le seul EPG hors ligne connu.
     */
    @Transaction
    suspend fun replaceEpgForStream(streamId: Int, entries: List<EpgCacheEntity>) {
        if (entries.isEmpty()) return
        deleteEpgCache(streamId)
        insertEpgEntries(entries)
    }

    /**
     * Purge de rétention : sans elle, la clé composite fait croître la table
     * indéfiniment au fil des consultations.
     */
    @Query("DELETE FROM epg_cache WHERE endTimestamp < :threshold")
    suspend fun purgeExpiredEpg(threshold: Long)

    @Query("DELETE FROM epg_cache")
    suspend fun clearEpgCache()
}
