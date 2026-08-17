package com.cstv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cstv.app.data.local.entity.PlaybackRepairProfileEntity

@Dao
interface PlaybackRepairProfileDao {

    @Query("SELECT * FROM playback_repair_profiles WHERE mediaUid = :mediaUid LIMIT 1")
    suspend fun getByMediaUid(mediaUid: Long): PlaybackRepairProfileEntity?

    /** La configuration gagnante remplace toute configuration mémorisée précédente (T23 §7.3). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: PlaybackRepairProfileEntity)

    /** Profil mémorisé obsolète : supprimé après un nouvel échec net (T23 §8.4, §7.4). */
    @Query("DELETE FROM playback_repair_profiles WHERE mediaUid = :mediaUid")
    suspend fun deleteByMediaUid(mediaUid: Long)
}
