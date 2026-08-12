package com.cstv.app.data.local.dao

import androidx.room.*
import com.cstv.app.data.local.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT * FROM profiles ORDER BY createdAt ASC")
    suspend fun getAll(): List<ProfileEntity>

    @Query("SELECT * FROM profiles ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE remoteId IS NULL ORDER BY createdAt ASC")
    suspend fun getAllWithoutRemoteId(): List<ProfileEntity>

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun count(): Int

    @Insert
    suspend fun insert(profile: ProfileEntity): Long

    @Update
    suspend fun update(profile: ProfileEntity)

    @Query("UPDATE profiles SET remoteId = :remoteId WHERE id = :id")
    suspend fun setRemoteId(id: Int, remoteId: String?)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM profiles")
    suspend fun deleteAll()
}
