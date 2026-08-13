package com.cstv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cstv.app.data.local.entity.CategoryRefEntity

@Dao
interface CategoryRefDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(ref: CategoryRefEntity): Long

    @Query("SELECT catUid FROM category_refs WHERE accountKey = :accountKey AND kind = :kind AND providerCategoryId = :categoryId LIMIT 1")
    suspend fun findUid(accountKey: String, kind: String, categoryId: String): Long?

    suspend fun resolve(accountKey: String, kind: String, categoryId: String): Long {
        insertIgnore(CategoryRefEntity(accountKey = accountKey, kind = kind, providerCategoryId = categoryId))
        return checkNotNull(findUid(accountKey, kind, categoryId))
    }

    @Query("UPDATE category_refs SET accountKey = :accountKey WHERE accountKey = ''")
    suspend fun bindUnassigned(accountKey: String): Int

    /** See [MediaRefDao.purgeUnreferenced] — same rule, checked against `category_preferences`. */
    @Query("DELETE FROM category_refs WHERE catUid NOT IN (SELECT catUid FROM category_preferences)")
    suspend fun purgeUnreferenced()
}
