package com.cstv.app.presentation.series

import com.cstv.app.domain.model.PlaybackPosition
import com.cstv.app.domain.model.SeriesCategory
import com.cstv.app.domain.model.SeriesDetails
import com.cstv.app.domain.model.SeriesStream

data class SeriesState(
    val categories: List<SeriesCategory> = emptyList(),
    val selectedCategory: SeriesCategory? = null,
    val streams: List<SeriesStream> = emptyList(),
    val selectedStream: SeriesStream? = null,
    val selectedSeriesDetails: SeriesDetails? = null,
    // Séries associées (mêmes genres) affichées en bas des détails.
    val relatedSeries: List<SeriesStream> = emptyList(),
    val isLoadingCategories: Boolean = false,
    val isLoadingStreams: Boolean = false,
    val isLoadingDetails: Boolean = false,
    val error: String? = null,
    // Compteur de séries par categoryId (cache local), pour la bottom sheet.
    val categoryCounts: Map<String, Int> = emptyMap(),
    val resumeSeries: List<PlaybackPosition> = emptyList()
)
