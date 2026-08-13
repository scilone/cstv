package com.cstv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cstv.app.data.local.entity.TrackPreferenceEntity

data class TrackPreferenceValues(val audioLang: String?, val subtitleLang: String?)
data class TrackPreferenceRow(val providerId: Int, val kind: String, val audioLang: String?, val subtitleLang: String?)

@Dao
interface TrackPreferenceDao {

    @Query(
        "SELECT r.providerId AS providerId, r.kind AS kind, tp.audioLang AS audioLang, tp.subtitleLang AS subtitleLang " +
            "FROM track_preferences tp JOIN media_refs r ON r.mediaUid = tp.mediaUid WHERE tp.profileId = :profileId AND r.accountKey = :accountKey"
    )
    suspend fun getAllForProfile(profileId: Int, accountKey: String): List<TrackPreferenceRow>

    @Query(
        "SELECT tp.audioLang AS audioLang, tp.subtitleLang AS subtitleLang FROM track_preferences tp " +
            "JOIN media_refs r ON r.mediaUid = tp.mediaUid " +
            "WHERE tp.profileId = :profileId AND r.accountKey = :accountKey AND r.kind = :kind AND r.providerId = :providerId LIMIT 1"
    )
    suspend fun getPreference(profileId: Int, accountKey: String, kind: String, providerId: Int): TrackPreferenceValues?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preference: TrackPreferenceEntity)

    @Query("DELETE FROM track_preferences WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: Int)

    /** T20 (§4.6): cloud sync projection — same shape as [getAllForProfile], named for clarity. */
    @Query(
        "SELECT r.providerId AS providerId, r.kind AS kind, tp.audioLang AS audioLang, tp.subtitleLang AS subtitleLang " +
            "FROM track_preferences tp JOIN media_refs r ON r.mediaUid = tp.mediaUid WHERE tp.profileId = :profileId AND r.accountKey = :accountKey"
    )
    suspend fun wireRows(profileId: Int, accountKey: String): List<TrackPreferenceRow>
}
