package com.cstv.app.domain.model

data class PopularTop10Result(
    val movies: List<VodStream>?,
    val series: List<SeriesStream>?
)
