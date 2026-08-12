package com.cstv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cstv.app.data.local.entity.ProfileSyncStateEntity

@Dao
interface ProfileSyncStateDao {
    @Query("SELECT * FROM profile_sync_state WHERE profileId = :profileId AND namespace = :namespace LIMIT 1")
    suspend fun get(profileId: Int, namespace: String): ProfileSyncStateEntity?

    @Query("SELECT * FROM profile_sync_state WHERE profileId = :profileId")
    suspend fun getForProfile(profileId: Int): List<ProfileSyncStateEntity>

    @Query("SELECT * FROM profile_sync_state WHERE pending = 1")
    suspend fun getPending(): List<ProfileSyncStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: ProfileSyncStateEntity)

    @Query("DELETE FROM profile_sync_state WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: Int)

    @Query("DELETE FROM profile_sync_state")
    suspend fun deleteAll()
}
