package com.cstv.app.data.local.entity

import androidx.room.Entity

@Entity(tableName = "playback_positions", primaryKeys = ["streamId", "profileId"])
data class PlaybackPositionEntity(
    val streamId: Int,
    val profileId: Int,
    val positionMs: Long,
    val durationMs: Long,
    val lastAccessedAt: Long,
    val title: String? = null,
    val coverUrl: String? = null,
    val type: String? = null, // "movie" or "series"
    val containerExtension: String? = null,
    val seriesId: Int? = null,
    val episodeNum: Int? = null,
    val seasonNum: Int? = null,
    val plot: String? = null,
    val duration: String? = null,
    val releaseDate: String? = null,
    val categoryId: String? = null
)
