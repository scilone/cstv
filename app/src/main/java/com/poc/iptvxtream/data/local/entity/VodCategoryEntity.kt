package com.poc.iptvxtream.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vod_categories")
data class VodCategoryEntity(
    @PrimaryKey val categoryId: String,
    val categoryName: String,
    val parentId: Int,
    val cachedAt: Long,
    val orderIndex: Int = 0
)
