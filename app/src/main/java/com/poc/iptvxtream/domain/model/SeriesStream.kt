package com.poc.iptvxtream.domain.model

data class SeriesStream(
    val seriesId: Int,
    val name: String,
    val cover: String?,
    val rating: String?,
    val added: String?,
    val categoryId: String
)
