package com.poc.iptvxtream.data.local.dao

import androidx.room.*
import com.poc.iptvxtream.data.local.entity.FavoriteEntity
import com.poc.iptvxtream.data.local.entity.LiveStreamEntity
import com.poc.iptvxtream.data.local.entity.SeriesStreamEntity
import com.poc.iptvxtream.data.local.entity.VodStreamEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritesDao {

    // Phase 41 : Room ré-émet automatiquement à chaque écriture sur `favorites`
    // (addFavorite/removeFavorite), sans reload manuel depuis les ViewModels.
    @Query("SELECT * FROM favorites WHERE profileId = :profileId ORDER BY addedAt DESC")
    fun observeFavorites(profileId: Int): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE type = :type AND profileId = :profileId ORDER BY addedAt DESC")
    suspend fun getFavoritesByType(type: String, profileId: Int): List<FavoriteEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :id AND type = :type AND profileId = :profileId LIMIT 1)")
    suspend fun isFavorite(id: Int, type: String, profileId: Int): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE id = :id AND type = :type AND profileId = :profileId")
    suspend fun removeFavorite(id: Int, type: String, profileId: Int)

    @Query("DELETE FROM favorites WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: Int)

    // --- Unified Local Search (Phase 40 : FTS4 au lieu de LIKE '%x%', non
    // indexable par SQLite en préfixe libre -> full scan à chaque frappe) ---
    @Query(
        """
        SELECT live_streams.* FROM live_streams
        JOIN live_streams_fts ON live_streams.streamId = live_streams_fts.rowid
        WHERE live_streams_fts MATCH :matchQuery
        ORDER BY live_streams.name ASC
        """
    )
    suspend fun searchLiveStreams(matchQuery: String): List<LiveStreamEntity>

    @Query(
        """
        SELECT vod_streams.* FROM vod_streams
        JOIN vod_streams_fts ON vod_streams.streamId = vod_streams_fts.rowid
        WHERE vod_streams_fts MATCH :matchQuery
        ORDER BY vod_streams.name ASC
        """
    )
    suspend fun searchVodStreams(matchQuery: String): List<VodStreamEntity>

    @Query(
        """
        SELECT series_streams.* FROM series_streams
        JOIN series_streams_fts ON series_streams.seriesId = series_streams_fts.rowid
        WHERE series_streams_fts MATCH :matchQuery
        ORDER BY series_streams.name ASC
        """
    )
    suspend fun searchSeriesStreams(matchQuery: String): List<SeriesStreamEntity>
}
