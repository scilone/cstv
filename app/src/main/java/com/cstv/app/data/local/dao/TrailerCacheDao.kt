package com.cstv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cstv.app.data.local.entity.TrailerCacheEntity

@Dao
interface TrailerCacheDao {
    @Query("SELECT * FROM trailer_cache WHERE mediaType = :mediaType AND catalogId = :catalogId") suspend fun get(mediaType: String, catalogId: Int): TrailerCacheEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(entry: TrailerCacheEntity)
    @Query("DELETE FROM trailer_cache WHERE mediaType = :mediaType AND catalogId = :catalogId") suspend fun delete(mediaType: String, catalogId: Int)
    @Query("DELETE FROM trailer_cache") suspend fun clear()
}
