package com.cstv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cstv.app.data.local.entity.ContentClassificationEntity

@Dao
interface ContentClassificationDao {
    @Query("SELECT * FROM content_classifications WHERE kind = :kind AND providerId = :providerId LIMIT 1")
    suspend fun find(kind: String, providerId: Int): ContentClassificationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ContentClassificationEntity)
}
