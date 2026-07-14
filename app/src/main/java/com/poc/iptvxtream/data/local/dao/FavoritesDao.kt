package com.poc.iptvxtream.data.local.dao

import androidx.room.*
import com.poc.iptvxtream.data.local.entity.FavoriteEntity
import com.poc.iptvxtream.data.local.entity.LiveStreamEntity
import com.poc.iptvxtream.data.local.entity.SeriesStreamEntity
import com.poc.iptvxtream.data.local.entity.VodStreamEntity

@Dao
interface FavoritesDao {

    @Query("SELECT * FROM favorites WHERE profileId = :profileId ORDER BY addedAt DESC")
    suspend fun getAllFavorites(profileId: Int): List<FavoriteEntity>

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

    // --- Unified Local Search ---
    @Query("SELECT * FROM live_streams WHERE name LIKE :query ORDER BY name ASC")
    suspend fun searchLiveStreams(query: String): List<LiveStreamEntity>

    @Query("SELECT * FROM vod_streams WHERE name LIKE :query OR actors LIKE :query OR director LIKE :query OR genre LIKE :query ORDER BY name ASC")
    suspend fun searchVodStreams(query: String): List<VodStreamEntity>

    @Query("SELECT * FROM series_streams WHERE name LIKE :query OR actors LIKE :query OR director LIKE :query OR genre LIKE :query ORDER BY name ASC")
    suspend fun searchSeriesStreams(query: String): List<SeriesStreamEntity>
}
