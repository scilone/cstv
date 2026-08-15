package com.cstv.app.domain.model

data class PlaybackPosition(
    val streamId: Int,
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
    val categoryId: String? = null,
    val genre: String? = null
)
