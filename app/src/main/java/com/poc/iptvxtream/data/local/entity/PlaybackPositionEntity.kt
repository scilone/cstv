package com.poc.iptvxtream.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_positions")
data class PlaybackPositionEntity(
    @PrimaryKey val streamId: Int,
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
    val releaseDate: String? = null
)
