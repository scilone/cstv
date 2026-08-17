package com.cstv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cstv.app.data.local.entity.MediaRefEntity

@Dao
interface MediaRefDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(ref: MediaRefEntity): Long

    @Query("SELECT mediaUid FROM media_refs WHERE accountKey = :accountKey AND kind = :kind AND providerId = :providerId LIMIT 1")
    suspend fun findUid(accountKey: String, kind: String, providerId: Int): Long?

    suspend fun resolve(accountKey: String, kind: String, providerId: Int): Long {
        insertIgnore(MediaRefEntity(accountKey = accountKey, kind = kind, providerId = providerId))
        return checkNotNull(findUid(accountKey, kind, providerId))
    }

    @Query("UPDATE media_refs SET accountKey = :accountKey WHERE accountKey = ''")
    suspend fun bindUnassigned(accountKey: String): Int

    /**
     * An identity with no referencing state has no reason to survive; one still referenced by a
     * state row survives the disappearance of its media (the "inactive identity" requirement).
     * Checked against all seven state tables that carry `mediaUid`.
     */
    @Query(
        "DELETE FROM media_refs WHERE mediaUid NOT IN (" +
            "SELECT mediaUid FROM favorites " +
            "UNION SELECT mediaUid FROM playback_positions " +
            "UNION SELECT mediaUid FROM recently_watched_live " +
            "UNION SELECT mediaUid FROM media_ratings " +
            "UNION SELECT mediaUid FROM track_preferences " +
            "UNION SELECT mediaUid FROM series_watch_state " +
            "UNION SELECT mediaUid FROM downloaded_media " +
            "UNION SELECT mediaUid FROM playback_repair_profiles)"
    )
    suspend fun purgeUnreferenced()
}
