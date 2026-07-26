package com.cstv.app.presentation.vod

import com.cstv.app.domain.model.PlaybackPosition
import com.cstv.app.domain.model.VodCategory
import com.cstv.app.domain.model.VodDetails
import com.cstv.app.domain.model.VodStream
import com.cstv.app.domain.model.MediaRatingValue

data class VodState(
    val categories: List<VodCategory> = emptyList(),
    val selectedCategory: VodCategory? = null,
    val streams: List<VodStream> = emptyList(),
    val selectedStreamId: Int? = null,
    val selectedVodDetails: VodDetails? = null,
    // Films associés (mêmes genres) affichés en bas des détails.
    val relatedStreams: List<VodStream> = emptyList(),
    val isLoadingCategories: Boolean = false,
    val isLoadingStreams: Boolean = false,
    val isLoadingDetails: Boolean = false,
    val error: String? = null,
    // Compteur de films par categoryId (cache local), pour la bottom sheet.
    val categoryCounts: Map<String, Int> = emptyMap(),
    val resumeMovies: List<PlaybackPosition> = emptyList(),
    val isRemovingHistory: Boolean = false,
    val historyRemovalError: String? = null,
    val mediaRating: MediaRatingValue? = null,
    val isRatingSaving: Boolean = false,
    val ratingError: String? = null,
    /** Fraîcheur du catalogue local : alimente la bannière hors ligne. */
    val catalogStatus: com.cstv.app.domain.sync.CatalogStatus = com.cstv.app.domain.sync.CatalogStatus()
)
