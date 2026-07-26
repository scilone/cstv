package com.cstv.app.presentation.livetv

import com.cstv.app.domain.model.LiveCategory
import com.cstv.app.domain.model.LiveEpgProgram
import com.cstv.app.domain.model.LiveStream

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
    val categoryCounts: Map<String, Int> = emptyMap(),
    val isRemovingHistory: Boolean = false,
    val historyRemovalError: String? = null,
    /** Fraîcheur du catalogue local : alimente la bannière hors ligne. */
    val catalogStatus: com.cstv.app.domain.sync.CatalogStatus = com.cstv.app.domain.sync.CatalogStatus()
)
