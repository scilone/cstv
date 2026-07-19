package com.cstv.app.domain.model

data class TrendingCatalogItem(
    val trendingTitle: TrendingTitle,
    val matchedMovie: VodStream? = null,
    val matchedSeries: SeriesStream? = null
)
