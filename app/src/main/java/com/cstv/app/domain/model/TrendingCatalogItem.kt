package com.cstv.app.domain.model

data class TrendingCatalogItem(
    val trendingTitle: TrendingTitle,
    val matchedMovies: List<VodStream> = emptyList(),
    val matchedSeriesList: List<SeriesStream> = emptyList(),
    val matchedMovie: VodStream? = null,
    val matchedSeries: SeriesStream? = null
)
