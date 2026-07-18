package com.cstv.app.data.local.entity

import androidx.room.Entity

@Entity(tableName = "recently_watched_live", primaryKeys = ["streamId", "profileId"])
data class RecentlyWatchedLiveEntity(
    val streamId: Int,
    val profileId: Int,
    val name: String,
    val streamIcon: String?,
    val categoryId: String?,
    val num: Int?,
    val watchedAt: Long
)
