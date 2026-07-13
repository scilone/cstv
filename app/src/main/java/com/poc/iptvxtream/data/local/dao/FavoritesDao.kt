package com.poc.iptvxtream.data.local.dao

import androidx.room.*
import com.poc.iptvxtream.data.local.entity.FavoriteEntity
import com.poc.iptvxtream.data.local.entity.LiveStreamEntity
import com.poc.iptvxtream.data.local.entity.SeriesStreamEntity
import com.poc.iptvxtream.data.local.entity.VodStreamEntity

@Dao
interface FavoritesDao {

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    suspend fun getAllFavorites(): List<FavoriteEntity>

    @Query("SELECT * FROM favorites WHERE type = :type ORDER BY addedAt DESC")
    suspend fun getFavoritesByType(type: String): List<FavoriteEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :id AND type = :type LIMIT 1)")
    suspend fun isFavorite(id: Int, type: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE id = :id AND type = :type")
    suspend fun removeFavorite(id: Int, type: String)

    // --- Unified Local Search ---
    @Query("SELECT * FROM live_streams WHERE name LIKE :query ORDER BY name ASC")
    suspend fun searchLiveStreams(query: String): List<LiveStreamEntity>

    @Query("SELECT * FROM vod_streams WHERE name LIKE :query OR actors LIKE :query OR director LIKE :query OR genre LIKE :query ORDER BY name ASC")
    suspend fun searchVodStreams(query: String): List<VodStreamEntity>

    @Query("SELECT * FROM series_streams WHERE name LIKE :query OR actors LIKE :query OR director LIKE :query OR genre LIKE :query ORDER BY name ASC")
    suspend fun searchSeriesStreams(query: String): List<SeriesStreamEntity>
}
