package com.cstv.app.data.local.entity

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
    val cachedAt: Long,
    val actors: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val orderIndex: Int = 0,
    val releaseYear: Int? = null
)
