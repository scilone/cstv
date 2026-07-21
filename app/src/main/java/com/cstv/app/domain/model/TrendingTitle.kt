package com.cstv.app.domain.model

data class TrendingTitle(
    val tmdbId: Int,
    val title: String,
    val isMovie: Boolean,
    val year: Int?,
    val posterUrl: String?
)
