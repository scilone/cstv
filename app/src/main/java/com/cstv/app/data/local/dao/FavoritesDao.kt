package com.cstv.app.data.local.dao

import androidx.room.*
import com.cstv.app.data.local.entity.FavoriteEntity
import com.cstv.app.data.local.entity.LiveStreamEntity
import com.cstv.app.data.local.entity.SeriesStreamEntity
import com.cstv.app.data.local.entity.VodStreamEntity
import kotlinx.coroutines.flow.Flow

internal const val SEARCH_TEXT_LIKE_PREDICATE = "searchText LIKE :pattern ESCAPE '\\'"

/** T20: display projection — favorites joined to the current catalogue, filtered by account. */
data class FavoriteListRow(
    val providerId: Int,
    val kind: String,
    val addedAt: Long,
    val name: String,
    val cover: String?,
    val categoryId: String,
)

/** T20: wire projection for cloud sync — no catalogue metadata, see [FavoritesDao.wireRows]. */
data class FavoriteWireRow(val providerId: Int, val kind: String, val addedAt: Long)

/** Visibilité élargie pour [com.cstv.app.data.local.db.StateDisplayJoinSqlTest], qui rejoue le SQL
 *  réel plutôt qu'une copie qui pourrait diverger silencieusement. */
internal const val FAVORITE_LIST_QUERY = """
    SELECT r.providerId AS providerId, r.kind AS kind, f.addedAt AS addedAt, s.name AS name, s.streamIcon AS cover, s.categoryId AS categoryId
      FROM favorites f JOIN media_refs r ON r.mediaUid = f.mediaUid
      JOIN live_streams s ON s.streamId = r.providerId
     WHERE f.profileId = :profileId AND r.accountKey = :accountKey AND r.kind = 'live'
    UNION ALL
    SELECT r.providerId, r.kind, f.addedAt, s.name, s.streamIcon, s.categoryId
      FROM favorites f JOIN media_refs r ON r.mediaUid = f.mediaUid
      JOIN vod_streams s ON s.streamId = r.providerId
     WHERE f.profileId = :profileId AND r.accountKey = :accountKey AND r.kind = 'movie'
    UNION ALL
    SELECT r.providerId, r.kind, f.addedAt, s.name, s.cover, s.categoryId
      FROM favorites f JOIN media_refs r ON r.mediaUid = f.mediaUid
      JOIN series_streams s ON s.seriesId = r.providerId
     WHERE f.profileId = :profileId AND r.accountKey = :accountKey AND r.kind = 'series'
     ORDER BY addedAt DESC
"""

@Dao
interface FavoritesDao {

    // Phase 41 : Room ré-émet automatiquement à chaque écriture sur `favorites`
    // (addFavorite/removeFavorite) ou sur le catalogue joint, sans reload manuel.
    @Query(FAVORITE_LIST_QUERY)
    fun observeFavorites(profileId: Int, accountKey: String): Flow<List<FavoriteListRow>>

    @Query(FAVORITE_LIST_QUERY)
    suspend fun getFavorites(profileId: Int, accountKey: String): List<FavoriteListRow>

    /** Séries favorites d'un profil, indépendamment du catalogue (F12 : suivi de séries). */
    @Query(
        "SELECT r.providerId FROM favorites f JOIN media_refs r ON r.mediaUid = f.mediaUid " +
            "WHERE f.profileId = :profileId AND r.accountKey = :accountKey AND r.kind = 'series'"
    )
    suspend fun getFavoriteSeriesProviderIds(profileId: Int, accountKey: String): List<Int>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM favorites f JOIN media_refs r ON r.mediaUid = f.mediaUid " +
            "WHERE f.profileId = :profileId AND r.accountKey = :accountKey AND r.kind = :kind AND r.providerId = :providerId LIMIT 1)"
    )
    suspend fun isFavorite(profileId: Int, accountKey: String, kind: String, providerId: Int): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query(
        "DELETE FROM favorites WHERE profileId = :profileId AND mediaUid = (" +
            "SELECT mediaUid FROM media_refs WHERE accountKey = :accountKey AND kind = :kind AND providerId = :providerId LIMIT 1)"
    )
    suspend fun removeFavorite(profileId: Int, accountKey: String, kind: String, providerId: Int)

    @Query("DELETE FROM favorites WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: Int)

    /** T19-R2/T20: makes the per-profile favourites cap persistent in Room, not just a
     *  snapshot-time `take(N)`. `rowid` is SQLite's implicit row id; `favorites` has no surrogate
     *  key column of its own (composite primary key), so it is the only stable per-row handle. */
    @Query(
        "DELETE FROM favorites WHERE profileId = :profileId AND rowid NOT IN (" +
            "SELECT rowid FROM favorites WHERE profileId = :profileId ORDER BY addedAt DESC LIMIT :limit)"
    )
    suspend fun pruneToMostRecent(profileId: Int, limit: Int)

    /** T20 (§4.6): cloud sync reads a projection, not the entity — no catalogue metadata leaves
     *  the device, and the format survives a media disappearing from the catalogue. */
    @Query(
        "SELECT r.providerId AS providerId, r.kind AS kind, f.addedAt AS addedAt " +
            "FROM favorites f JOIN media_refs r ON r.mediaUid = f.mediaUid " +
            "WHERE f.profileId = :profileId AND r.accountKey = :accountKey"
    )
    suspend fun wireRows(profileId: Int, accountKey: String): List<FavoriteWireRow>

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
