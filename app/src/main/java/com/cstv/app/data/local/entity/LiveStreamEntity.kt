package com.cstv.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "live_streams")
data class LiveStreamEntity(
    @PrimaryKey val streamId: Int,
    val name: String,
    val streamIcon: String?,
    val epgChannelId: String?,
    val num: Int,
    val categoryId: String,
    val cachedAt: Long
)
