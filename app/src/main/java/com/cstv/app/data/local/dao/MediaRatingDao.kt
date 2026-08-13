package com.cstv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cstv.app.data.local.entity.MediaRatingEntity
import kotlinx.coroutines.flow.Flow

/** T20: rating joined only to its identity (no catalogue metadata needed for a rating value). */
data class MediaRatingRow(val providerId: Int, val kind: String, val value: Int)

@Dao
interface MediaRatingDao {
    @Query(
        "SELECT mr.value FROM media_ratings mr JOIN media_refs r ON r.mediaUid = mr.mediaUid " +
            "WHERE mr.profileId = :profileId AND r.accountKey = :accountKey AND r.kind = :kind AND r.providerId = :providerId LIMIT 1"
    )
    fun observeRating(profileId: Int, accountKey: String, kind: String, providerId: Int): Flow<Int?>

    @Query(
        "SELECT r.providerId AS providerId, r.kind AS kind, mr.value AS value FROM media_ratings mr " +
            "JOIN media_refs r ON r.mediaUid = mr.mediaUid WHERE mr.profileId = :profileId AND r.accountKey = :accountKey"
    )
    suspend fun getAllForProfile(profileId: Int, accountKey: String): List<MediaRatingRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MediaRatingEntity)

    @Query(
        "DELETE FROM media_ratings WHERE profileId = :profileId AND mediaUid = (" +
            "SELECT mediaUid FROM media_refs WHERE accountKey = :accountKey AND kind = :kind AND providerId = :providerId LIMIT 1)"
    )
    suspend fun delete(profileId: Int, accountKey: String, kind: String, providerId: Int)

    @Query("DELETE FROM media_ratings WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: Int)

    /** T20 (§4.6): cloud sync projection, no catalogue metadata. */
    @Query(
        "SELECT r.providerId AS providerId, r.kind AS kind, mr.value AS value FROM media_ratings mr " +
            "JOIN media_refs r ON r.mediaUid = mr.mediaUid WHERE mr.profileId = :profileId AND r.accountKey = :accountKey"
    )
    suspend fun wireRows(profileId: Int, accountKey: String): List<MediaRatingRow>
}
