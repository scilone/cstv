package com.cstv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cstv.app.data.local.entity.CategoryPreferenceEntity

@Dao
interface CategoryPreferenceDao {

    @Query("SELECT * FROM category_preferences WHERE type = :type AND profileId = :profileId")
    suspend fun getForProfile(type: String, profileId: Int): List<CategoryPreferenceEntity>

    @Query(
        "SELECT * FROM category_preferences WHERE categoryId = :categoryId AND type = :type AND profileId = :profileId LIMIT 1"
    )
    suspend fun get(categoryId: String, type: String, profileId: Int): CategoryPreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preference: CategoryPreferenceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(preferences: List<CategoryPreferenceEntity>)

    @Query("DELETE FROM category_preferences WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: Int)
}
