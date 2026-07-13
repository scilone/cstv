package com.poc.iptvxtream.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "epg_cache")
data class EpgCacheEntity(
    @PrimaryKey val streamId: Int,
    val title: String,
    val description: String?,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val cachedAt: Long
)