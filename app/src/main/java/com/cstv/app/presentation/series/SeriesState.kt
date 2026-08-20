package com.cstv.app.presentation.series

import com.cstv.app.domain.model.PlaybackPosition
import com.cstv.app.domain.model.SeriesCategory
import com.cstv.app.domain.model.SeriesDetails
import com.cstv.app.domain.model.SeriesStream
import com.cstv.app.domain.model.MediaRatingValue
import com.cstv.app.presentation.components.TrailerPreviewUiState
import com.cstv.app.domain.model.AdvancedSearchFilter

data class SeriesState(
    val categories: List<SeriesCategory> = emptyList(),
    val selectedCategory: SeriesCategory? = null,
    val streams: List<SeriesStream> = emptyList(),
    val selectedStreamId: Int? = null,
    val selectedSeriesDetails: SeriesDetails? = null,
    /** Classification exacte (F45 §8.13) affichée sur la fiche, quel que soit le profil. */
    val ageRating: Int? = null,
    val isLoadingAgeRating: Boolean = false,
    // Séries associées (mêmes genres) affichées en bas des détails.
    val relatedSeries: List<SeriesStream> = emptyList(),
    val isLoadingCategories: Boolean = false,
    val isLoadingStreams: Boolean = false,
    val isLoadingDetails: Boolean = false,
    val error: String? = null,
    /** F44 : profil bridé, PIN requis pour cette œuvre précise. */
    val parentalPinRequest: com.cstv.app.domain.usecase.PlaybackAvailability.RequiresParentalPin? = null,
    val parentalPinFeedback: com.cstv.app.domain.model.ParentalPinFeedback? = null,
    // Compteur de séries par categoryId (cache local), pour la bottom sheet.
    val categoryCounts: Map<String, Int> = emptyMap(),
    /** F22 — filters are scoped to the selected TV category. */
    val advancedFilter: AdvancedSearchFilter = AdvancedSearchFilter.DEFAULT,
    val isFilterSheetOpen: Boolean = false,
    val availableGenres: List<String> = emptyList(),
    val categoryYearRange: IntRange = 1980..2025,
    val filteredCount: Int = 0,
    val resumeSeries: List<PlaybackPosition> = emptyList(),
    val isRemovingHistory: Boolean = false,
    val historyRemovalError: String? = null,
    val mediaRating: MediaRatingValue? = null,
    val isRatingSaving: Boolean = false,
    val ratingError: String? = null,
    val trailerPreview: TrailerPreviewUiState = TrailerPreviewUiState.Poster,
    /** Fraîcheur du catalogue local : alimente la bannière hors ligne. */
    val catalogStatus: com.cstv.app.domain.sync.CatalogStatus = com.cstv.app.domain.sync.CatalogStatus()
)
