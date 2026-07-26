package com.cstv.app.data.local.entity

import androidx.room.Entity

@Entity(tableName = "trailer_cache", primaryKeys = ["mediaType", "catalogId"])
data class TrailerCacheEntity(
    val mediaType: String,
    val catalogId: Int,
    val videoId: String?,
    val source: String?,
    val resolvedTmdbId: Int?,
    val resolvedAt: Long
)
