package com.cstv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cstv.app.data.local.entity.TrackPreferenceEntity

@Dao
interface TrackPreferenceDao {

    @Query("SELECT * FROM track_preferences WHERE profileId = :profileId AND mediaType = :mediaType AND mediaId = :mediaId LIMIT 1")
    suspend fun getPreference(profileId: Int, mediaType: String, mediaId: Int): TrackPreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preference: TrackPreferenceEntity)

    @Query("DELETE FROM track_preferences WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: Int)
}
