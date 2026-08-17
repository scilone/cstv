package com.cstv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cstv.app.data.local.entity.SeriesVersionPreferenceEntity

@Dao
interface SeriesVersionPreferenceDao {

    @Query("SELECT preferredSeriesId FROM series_version_preferences WHERE profileId = :profileId AND linkKey = :linkKey LIMIT 1")
    suspend fun getPreferredSeriesId(profileId: Int, linkKey: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preference: SeriesVersionPreferenceEntity)

    /** F39 §8.3 : suppression paresseuse quand la série préférée n'a plus l'épisode équivalent. */
    @Query("DELETE FROM series_version_preferences WHERE profileId = :profileId AND linkKey = :linkKey")
    suspend fun delete(profileId: Int, linkKey: String)

    @Query("DELETE FROM series_version_preferences WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: Int)
}
