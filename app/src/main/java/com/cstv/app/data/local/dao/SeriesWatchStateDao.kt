package com.cstv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cstv.app.data.local.entity.SeriesWatchStateEntity

data class SeriesWatchStateRow(
    val providerId: Int,
    val lastKnownSeason: Int,
    val lastKnownEpisode: Int,
    val lastNotifiedSeason: Int,
    val lastNotifiedEpisode: Int,
    val updatedAt: Long,
)

private const val SERIES_WATCH_STATE_QUERY = "SELECT r.providerId AS providerId, sw.lastKnownSeason AS lastKnownSeason, " +
    "sw.lastKnownEpisode AS lastKnownEpisode, sw.lastNotifiedSeason AS lastNotifiedSeason, " +
    "sw.lastNotifiedEpisode AS lastNotifiedEpisode, sw.updatedAt AS updatedAt " +
    "FROM series_watch_state sw JOIN media_refs r ON r.mediaUid = sw.mediaUid " +
    "WHERE sw.profileId = :profileId AND r.accountKey = :accountKey"

@Dao
interface SeriesWatchStateDao {

    @Query(SERIES_WATCH_STATE_QUERY)
    suspend fun getAllForProfile(profileId: Int, accountKey: String): List<SeriesWatchStateRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SeriesWatchStateEntity)

    @Query("DELETE FROM series_watch_state WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: Int)

    /** T20 (§4.6): cloud sync projection, same shape as [getAllForProfile]. */
    @Query(SERIES_WATCH_STATE_QUERY)
    suspend fun wireRows(profileId: Int, accountKey: String): List<SeriesWatchStateRow>
}
