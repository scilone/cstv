package com.cstv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.cstv.app.data.local.entity.EpgCacheEntity
import com.cstv.app.data.local.entity.LiveCategoryEntity
import com.cstv.app.data.local.entity.LiveStreamEntity
import com.cstv.app.data.local.entity.RecentlyWatchedLiveEntity
import com.cstv.app.domain.model.LocalSearchQuery
import kotlinx.coroutines.flow.Flow

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

    /** Ne jamais appeler directement : contourne le calcul de [LiveStreamEntity.searchText]. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
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

    @Transaction
    suspend fun replaceAllStreams(streams: List<LiveStreamEntity>) {
        if (streams.isEmpty()) return
        clearAllStreams()
        streams.chunked(VodDao.INSERT_CHUNK_SIZE).forEach { insertStreams(it) }
    }

    @Transaction
    suspend fun replaceStreamsByCategory(categoryId: String, streams: List<LiveStreamEntity>) {
        if (streams.isEmpty()) return
        clearStreamsByCategory(categoryId)
        streams.chunked(VodDao.INSERT_CHUNK_SIZE).forEach { insertStreams(it) }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentlyWatched(recentlyWatched: RecentlyWatchedLiveEntity)

    @Query("SELECT * FROM recently_watched_live WHERE profileId = :profileId ORDER BY watchedAt DESC LIMIT :limit")
    suspend fun getRecentlyWatched(profileId: Int, limit: Int): List<RecentlyWatchedLiveEntity>

    @Query("SELECT * FROM recently_watched_live WHERE profileId = :profileId ORDER BY watchedAt DESC LIMIT :limit")
    fun observeRecentlyWatched(profileId: Int, limit: Int): Flow<List<RecentlyWatchedLiveEntity>>

    @Query("DELETE FROM recently_watched_live WHERE streamId = :streamId AND profileId = :profileId")
    suspend fun deleteRecentlyWatched(streamId: Int, profileId: Int)

    @Query("DELETE FROM recently_watched_live WHERE profileId = :profileId")
    suspend fun deleteRecentlyWatchedForProfile(profileId: Int)

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
