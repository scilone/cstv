package com.cstv.app.domain.model

data class SeriesStream(
    val seriesId: Int,
    val name: String,
    val cover: String?,
    val rating: String?,
    val added: String?,
    val categoryId: String,
    val genre: String? = null,
    val releaseYear: Int? = null,
    val actors: String? = null,
    val director: String? = null
)
