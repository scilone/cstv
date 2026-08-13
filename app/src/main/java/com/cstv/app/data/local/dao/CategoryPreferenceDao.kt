package com.cstv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cstv.app.data.local.entity.CategoryPreferenceEntity

data class CategoryPreferenceRow(val categoryId: String, val kind: String, val hidden: Boolean, val sortOrder: Int?)

@Dao
interface CategoryPreferenceDao {

    @Query(
        "SELECT cr.providerCategoryId AS categoryId, cr.kind AS kind, cp.hidden AS hidden, cp.sortOrder AS sortOrder " +
            "FROM category_preferences cp JOIN category_refs cr ON cr.catUid = cp.catUid " +
            "WHERE cp.profileId = :profileId AND cr.accountKey = :accountKey"
    )
    suspend fun getAllForProfile(profileId: Int, accountKey: String): List<CategoryPreferenceRow>

    @Query(
        "SELECT cr.providerCategoryId AS categoryId, cr.kind AS kind, cp.hidden AS hidden, cp.sortOrder AS sortOrder " +
            "FROM category_preferences cp JOIN category_refs cr ON cr.catUid = cp.catUid " +
            "WHERE cp.profileId = :profileId AND cr.accountKey = :accountKey AND cr.kind = :kind"
    )
    suspend fun getForProfile(profileId: Int, accountKey: String, kind: String): List<CategoryPreferenceRow>

    @Query(
        "SELECT cp.hidden AS hidden, cp.sortOrder AS sortOrder FROM category_preferences cp " +
            "JOIN category_refs cr ON cr.catUid = cp.catUid " +
            "WHERE cp.profileId = :profileId AND cr.accountKey = :accountKey AND cr.kind = :kind AND cr.providerCategoryId = :categoryId LIMIT 1"
    )
    suspend fun get(profileId: Int, accountKey: String, kind: String, categoryId: String): CategoryPreferenceValues?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preference: CategoryPreferenceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(preferences: List<CategoryPreferenceEntity>)

    @Query("DELETE FROM category_preferences WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: Int)

    /** T20 (§4.6): cloud sync projection, same shape as [getAllForProfile]. */
    @Query(
        "SELECT cr.providerCategoryId AS categoryId, cr.kind AS kind, cp.hidden AS hidden, cp.sortOrder AS sortOrder " +
            "FROM category_preferences cp JOIN category_refs cr ON cr.catUid = cp.catUid " +
            "WHERE cp.profileId = :profileId AND cr.accountKey = :accountKey"
    )
    suspend fun wireRows(profileId: Int, accountKey: String): List<CategoryPreferenceRow>
}

data class CategoryPreferenceValues(val hidden: Boolean, val sortOrder: Int?)
