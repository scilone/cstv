package com.poc.iptvxtream.presentation.livetv

import com.poc.iptvxtream.domain.model.LiveCategory
import com.poc.iptvxtream.domain.model.LiveEpgProgram
import com.poc.iptvxtream.domain.model.LiveStream

data class LiveTvState(
    val categories: List<LiveCategory> = emptyList(),
    val selectedCategory: LiveCategory? = null,
    val streams: List<LiveStream> = emptyList(),
    val selectedStream: LiveStream? = null,
    val isLoadingCategories: Boolean = false,
    val isLoadingStreams: Boolean = false,
    val error: String? = null,
    val recentlyWatched: List<LiveStream> = emptyList(),
    val epgPrograms: Map<Int, LiveEpgProgram> = emptyMap(),
    // Compteur de chaînes par categoryId (cache local), pour la bottom sheet.
    val categoryCounts: Map<String, Int> = emptyMap()
)
