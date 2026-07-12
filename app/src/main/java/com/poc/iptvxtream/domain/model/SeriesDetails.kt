package com.poc.iptvxtream.domain.model

data class SeriesDetails(
    val seriesId: Int,
    val name: String,
    val cover: String?,
    val rating: String?,
    val seasons: List<SeriesSeason>,
    val episodes: Map<Int, List<SeriesEpisode>>,
    val director: String? = null,
    val releaseDate: String? = null,
    val genre: String? = null,
    val plot: String? = null,
    val actors: String? = null
)
