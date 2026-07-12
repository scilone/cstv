package com.poc.iptvxtream.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "series_streams")
data class SeriesStreamEntity(
    @PrimaryKey val seriesId: Int,
    val name: String,
    val cover: String?,
    val rating: String?,
    val added: String?,
    val categoryId: String,
    val cachedAt: Long
)
