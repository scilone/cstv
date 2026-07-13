package com.poc.iptvxtream.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vod_streams")
data class VodStreamEntity(
    @PrimaryKey val streamId: Int,
    val name: String,
    val streamIcon: String?,
    val rating: String?,
    val added: String?,
    val categoryId: String,
    val cachedAt: Long,
    val actors: String? = null,
    val director: String? = null,
    val genre: String? = null
)
