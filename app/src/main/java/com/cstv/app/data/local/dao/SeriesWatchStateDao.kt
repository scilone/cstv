package com.cstv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cstv.app.data.local.entity.SeriesWatchStateEntity

@Dao
interface SeriesWatchStateDao {

    @Query("SELECT * FROM series_watch_state WHERE profileId = :profileId")
    suspend fun getAllForProfile(profileId: Int): List<SeriesWatchStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SeriesWatchStateEntity)

    @Query("DELETE FROM series_watch_state WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: Int)
}
