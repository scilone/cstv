package com.poc.iptvxtream.presentation.vod

import com.poc.iptvxtream.domain.model.VodCategory
import com.poc.iptvxtream.domain.model.VodDetails
import com.poc.iptvxtream.domain.model.VodStream

data class VodState(
    val categories: List<VodCategory> = emptyList(),
    val selectedCategory: VodCategory? = null,
    val streams: List<VodStream> = emptyList(),
    val selectedStream: VodStream? = null,
    val selectedVodDetails: VodDetails? = null,
    val isLoadingCategories: Boolean = false,
    val isLoadingStreams: Boolean = false,
    val isLoadingDetails: Boolean = false,
    val error: String? = null,
    // Compteur de films par categoryId (cache local), pour la bottom sheet.
    val categoryCounts: Map<String, Int> = emptyMap()
)
