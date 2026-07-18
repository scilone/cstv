package com.cstv.app.domain.model

data class SeriesSeason(
    val seasonNumber: Int,
    val name: String,
    val episodeCount: Int,
    val cover: String?
)
