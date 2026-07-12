package com.poc.iptvxtream.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recently_watched_live")
data class RecentlyWatchedLiveEntity(
    @PrimaryKey val streamId: Int,
    val name: String,
    val streamIcon: String?,
    val categoryId: String?,
    val num: Int?,
    val watchedAt: Long
)