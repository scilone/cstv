package com.cstv.app.data.local.entity

import androidx.room.Entity

@Entity(tableName = "favorites", primaryKeys = ["id", "type", "profileId"])
data class FavoriteEntity(
    val id: Int,
    val type: String, // "live", "movie", "series"
    val name: String,
    val cover: String?,
    val categoryId: String,
    val addedAt: Long,
    val profileId: Int
)
