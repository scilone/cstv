package com.cstv.app.data.local.dao

import androidx.room.*
import com.cstv.app.data.local.entity.FavoriteEntity
import com.cstv.app.data.local.entity.LiveStreamEntity
import com.cstv.app.data.local.entity.SeriesStreamEntity
import com.cstv.app.data.local.entity.VodStreamEntity
import kotlinx.coroutines.flow.Flow

internal const val SEARCH_TEXT_LIKE_PREDICATE = "searchText LIKE :pattern ESCAPE '\\'"

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

    // --- Unified local substring search ---
    @Query(
        """
        SELECT * FROM live_streams WHERE $SEARCH_TEXT_LIKE_PREDICATE ORDER BY name ASC
        """
    )
    suspend fun searchLiveStreams(pattern: String): List<LiveStreamEntity>

    @Query(
        """
        SELECT * FROM vod_streams WHERE $SEARCH_TEXT_LIKE_PREDICATE ORDER BY name ASC
        """
    )
    suspend fun searchVodStreams(pattern: String): List<VodStreamEntity>

    @Query(
        """
        SELECT * FROM series_streams WHERE $SEARCH_TEXT_LIKE_PREDICATE ORDER BY name ASC
        """
    )
    suspend fun searchSeriesStreams(pattern: String): List<SeriesStreamEntity>
}
