package com.cstv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cstv.app.data.local.entity.MediaRatingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaRatingDao {
    @Query("SELECT * FROM media_ratings WHERE profileId = :profileId AND mediaType = :mediaType AND mediaId = :mediaId LIMIT 1")
    fun observeRating(profileId: Int, mediaType: String, mediaId: Int): Flow<MediaRatingEntity?>

    @Query("SELECT * FROM media_ratings WHERE profileId = :profileId")
    suspend fun getAllForProfile(profileId: Int): List<MediaRatingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MediaRatingEntity)

    @Query("DELETE FROM media_ratings WHERE profileId = :profileId AND mediaType = :mediaType AND mediaId = :mediaId")
    suspend fun delete(profileId: Int, mediaType: String, mediaId: Int)

    @Query("DELETE FROM media_ratings WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: Int)
}
