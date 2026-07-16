package com.poc.iptvxtream.presentation.series

import com.poc.iptvxtream.domain.model.SeriesCategory
import com.poc.iptvxtream.domain.model.SeriesDetails
import com.poc.iptvxtream.domain.model.SeriesStream

data class SeriesState(
    val categories: List<SeriesCategory> = emptyList(),
    val selectedCategory: SeriesCategory? = null,
    val streams: List<SeriesStream> = emptyList(),
    val selectedStream: SeriesStream? = null,
    val selectedSeriesDetails: SeriesDetails? = null,
    val isLoadingCategories: Boolean = false,
    val isLoadingStreams: Boolean = false,
    val isLoadingDetails: Boolean = false,
    val error: String? = null,
    // Compteur de séries par categoryId (cache local), pour la bottom sheet.
    val categoryCounts: Map<String, Int> = emptyMap()
)
