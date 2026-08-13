package com.cstv.app.presentation.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cstv.app.data.local.storage.CredentialsManager
import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.model.SeriesCategory
import com.cstv.app.domain.model.SeriesDetails
import com.cstv.app.domain.model.SeriesEpisode
import com.cstv.app.domain.model.SeriesStream
import com.cstv.app.domain.model.AdvancedSearchFilter
import com.cstv.app.domain.model.CatalogFilterMatcher
import com.cstv.app.domain.model.PlaybackPosition
import com.cstv.app.domain.usecase.GetSeriesCategoriesUseCase
import com.cstv.app.domain.usecase.GetSeriesCategoryCountsUseCase
import com.cstv.app.domain.usecase.GetSeriesDetailsUseCase
import com.cstv.app.domain.usecase.GetRelatedSeriesUseCase
import com.cstv.app.domain.usecase.GetSeriesStreamsUseCase
import com.cstv.app.domain.usecase.SavePlaybackPositionUseCase
import com.cstv.app.domain.usecase.RemoveFromContinueWatchingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import com.cstv.app.domain.model.MediaRatingValue
import com.cstv.app.domain.model.RatedMediaType
import com.cstv.app.domain.model.TrailerMedia
import com.cstv.app.domain.usecase.GetTrailerPreviewUseCase
import com.cstv.app.presentation.components.TrailerPreviewUiState
import javax.inject.Inject

import com.cstv.app.data.local.storage.SettingsManager
import com.cstv.app.data.local.storage.ResizeMode

@HiltViewModel
class SeriesViewModel @Inject constructor(
    private val getSeriesCategoriesUseCase: GetSeriesCategoriesUseCase,
    private val getSeriesCategoryCountsUseCase: GetSeriesCategoryCountsUseCase,
    private val getSeriesStreamsUseCase: GetSeriesStreamsUseCase,
    private val getSeriesDetailsUseCase: GetSeriesDetailsUseCase,
    private val getRelatedSeriesUseCase: GetRelatedSeriesUseCase,
    private val savePlaybackPositionUseCase: SavePlaybackPositionUseCase,
    private val credentialsManager: CredentialsManager,
    private val settingsManager: SettingsManager,
    private val trackPreferenceRepository: com.cstv.app.domain.repository.TrackPreferenceRepository,
    private val categoryPreferenceRepository: com.cstv.app.domain.repository.CategoryPreferenceRepository,
    private val vodRepository: com.cstv.app.domain.repository.VodRepository,
    private val seriesRepository: com.cstv.app.domain.repository.SeriesRepository,
    private val removeFromContinueWatchingUseCase: RemoveFromContinueWatchingUseCase,
    private val mediaRatingRepository: com.cstv.app.domain.repository.MediaRatingRepository,
    private val setMediaRatingUseCase: com.cstv.app.domain.usecase.SetMediaRatingUseCase,
    private val observeCatalogStatusUseCase: com.cstv.app.domain.usecase.ObserveCatalogStatusUseCase,
    private val catalogSyncManager: com.cstv.app.domain.sync.CatalogSyncManager,
    private val canPlayContentUseCase: com.cstv.app.domain.usecase.CanPlayContentUseCase,
    private val getTrailerPreviewUseCase: GetTrailerPreviewUseCase,
    private val invalidateTrailerPreviewUseCase: com.cstv.app.domain.usecase.InvalidateTrailerPreviewUseCase,
    @com.cstv.app.di.DefaultDispatcher
    private val computationDispatcher: kotlinx.coroutines.CoroutineDispatcher,
    private val markPlaybackSyncUseCase: com.cstv.app.domain.usecase.MarkPlaybackSyncUseCase? = null
) : ViewModel() {

    private val _state = MutableStateFlow(SeriesState())
    val state: StateFlow<SeriesState> = _state.asStateFlow()

    private val scrollPositions = mutableMapOf<String, Pair<Int, Int>>()
    private var ratingObservation: Job? = null
    private var streamsJob: Job? = null
    private var observedCategoryId: String? = null
    private var trailerJob: Job? = null
    private var activeTrailerMedia: TrailerMedia? = null

    fun saveScrollPosition(key: String, index: Int, offset: Int) {
        scrollPositions[key] = Pair(index, offset)
    }

    fun getScrollPosition(key: String): Pair<Int, Int> {
        return scrollPositions[key] ?: Pair(0, 0)
    }

    fun removeFromContinueWatching(position: PlaybackPosition) = viewModelScope.launch {
        _state.update { it.copy(isRemovingHistory = true, historyRemovalError = null) }
        try {
            removeFromContinueWatchingUseCase(position)
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            _state.update { it.copy(historyRemovalError = "Impossible de retirer cet élément. Réessayez.") }
        } finally {
            _state.update { it.copy(isRemovingHistory = false) }
        }
    }

    fun consumeHistoryRemovalError() { _state.update { it.copy(historyRemovalError = null) } }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pagedStreams: Flow<PagingData<SeriesStream>> = state
        .map { it.selectedCategory?.categoryId }
        .distinctUntilChanged()
        .flatMapLatest { categoryId ->
            if (categoryId != null) {
                seriesRepository.getSeriesStreamsPaged(categoryId)
                    .cachedIn(viewModelScope)
            } else {
                flowOf(PagingData.empty())
            }
        }

    init {
        observeCategories()
        observeCatalogStatus()
        triggerSilentSyncIfStale()
        // Observe et filtre les positions de lecture en temps réel (F5)
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                vodRepository.observeAllPlaybackPositions(),
                categoryPreferenceRepository.changes.onStart { emit(Unit) }
            ) { allPositions, _ ->
                allPositions
            }.collect { allPositions ->
                val hiddenSeries = try {
                    categoryPreferenceRepository.getPreferences(com.cstv.app.domain.model.CategoryType.SERIES)
                        .filterValues { it.hidden }
                        .keys
                } catch (e: Exception) {
                    emptySet()
                }

                // Get only "series" type playback positions
                val seriesPositions = allPositions.filter { pos ->
                    pos.type == "series" && pos.positionMs > 0 && pos.positionMs < (pos.durationMs - 15000L)
                }

                // Voir VodViewModel : la catégorie vient de la position (T9), pas
                // d'une relecture du catalogue complet à chaque entrée.
                val filtered = seriesPositions.filter { pos ->
                    val catId = pos.categoryId
                    catId == null || catId !in hiddenSeries
                }

                // Regroupe les épisodes par seriesId pour n'afficher qu'une seule entrée par série
                val seenSeriesIds = mutableSetOf<Int>()
                val uniqueFiltered = filtered.filter { pos ->
                    val seriesId = pos.seriesId
                    if (seriesId == null) true else seenSeriesIds.add(seriesId)
                }

                _state.update { it.copy(resumeSeries = uniqueFiltered) }
            }
        }
    }

    fun savePreferredAudio(lang: String?) {
        settingsManager.setPreferredAudio(lang)
    }

    fun savePreferredSubtitle(lang: String?) {
        settingsManager.setPreferredSubtitle(lang)
    }

    fun getPreferredAudio(): String? {
        return settingsManager.getPreferredAudio()
    }

    fun getPreferredSubtitle(): String? {
        return settingsManager.getPreferredSubtitle()
    }

    // --- Préférence de pistes par série (Phase 29, clé = seriesId, commune à
    // tous les épisodes, profil actif) ---
    suspend fun getSeriesTrackPreference(seriesId: Int) =
        trackPreferenceRepository.getPreference(com.cstv.app.domain.model.MediaType.SERIES, seriesId)

    fun saveSeriesAudio(seriesId: Int, lang: String?) {
        settingsManager.setPreferredAudio(lang)
        viewModelScope.launch {
            trackPreferenceRepository.saveAudioLang(com.cstv.app.domain.model.MediaType.SERIES, seriesId, lang)
        }
    }

    fun saveSeriesSubtitle(seriesId: Int, lang: String?) {
        settingsManager.setPreferredSubtitle(lang)
        viewModelScope.launch {
            trackPreferenceRepository.saveSubtitleLang(com.cstv.app.domain.model.MediaType.SERIES, seriesId, lang)
        }
    }

    fun getSubtitleStyle() = settingsManager.getSubtitleStyle()

    fun getResizeMode() = settingsManager.getResizeMode()
    fun setResizeMode(mode: ResizeMode) {
        settingsManager.setResizeMode(mode)
    }

    /** Voir `VodViewModel.observeCategories` : lecture Room, jamais réseau. */
    private fun observeCategories() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingCategories = it.categories.isEmpty()) }
            // Voir VodViewModel : mesure du second verrou d'affichage.
            val subscribedAt = System.nanoTime()
            var firstEmission = true
            getSeriesCategoriesUseCase().collect { categories ->
                if (firstEmission) {
                    firstEmission = false
                    com.cstv.app.di.IptvLog.d(
                        "PERF",
                        "Séries première émission catégories=${categories.size} " +
                            "en ${(System.nanoTime() - subscribedAt) / 1_000_000}ms"
                    )
                }
                val finalCategories = listOf(SeriesCategory("all", "Tout", 0)) + categories
                val previousSelectedId = _state.value.selectedCategory?.categoryId
                val newSelected = finalCategories.find { it.categoryId == previousSelectedId } ?: finalCategories.firstOrNull()
                _state.update {
                    it.copy(
                        categories = finalCategories,
                        selectedCategory = newSelected,
                        isLoadingCategories = false
                    )
                }
                newSelected?.let { observeStreams(it.categoryId) }
            }
        }
    }

    private fun observeCatalogStatus() {
        viewModelScope.launch {
            observeCatalogStatusUseCase().collect { status ->
                _state.update { it.copy(catalogStatus = status) }
            }
        }
    }

    /**
     * T7 (D3) : entrer sur l'onglet Séries déclenche silencieusement une
     * synchronisation si le catalogue est périmé — pas d'erreur, pas de
     * bannière, pas d'action manuelle. `syncIfStale()` est déjà un no-op si le
     * catalogue est frais, hors ligne, ou une synchronisation est en cours.
     */
    private fun triggerSilentSyncIfStale() {
        viewModelScope.launch {
            runCatching { catalogSyncManager.syncIfStale() }
        }
    }

    fun selectCategory(category: SeriesCategory) {
        _state.update {
            it.copy(
                selectedCategory = category,
                streams = emptyList(),
                advancedFilter = AdvancedSearchFilter.DEFAULT,
                isFilterSheetOpen = false,
                filteredCount = 0
            )
        }
        observeStreams(category.categoryId)
    }

    fun setFilterSheetOpen(isOpen: Boolean) { _state.update { it.copy(isFilterSheetOpen = isOpen) } }
    fun setMinRating(rating: Int?) { updateAdvancedFilter { it.copy(minRating = rating) } }
    fun setYearRange(range: IntRange?) { updateAdvancedFilter { filter ->
        val bounds = _state.value.categoryYearRange
        filter.copy(yearRange = range?.takeUnless { it.first <= bounds.first && it.last >= bounds.last })
    } }
    fun toggleGenre(genre: String) { updateAdvancedFilter { filter ->
        filter.copy(genres = if (genre in filter.genres) filter.genres - genre else filter.genres + genre)
    } }
    fun resetFilter() { updateAdvancedFilter { AdvancedSearchFilter.DEFAULT } }
    fun applyFilter() { _state.update { it.copy(isFilterSheetOpen = false) } }
    fun removeMinRatingFilter() = setMinRating(null)
    fun removeYearRangeFilter() = setYearRange(null)
    fun removeGenreFilter(genre: String) = toggleGenre(genre)

    private fun updateAdvancedFilter(transform: (AdvancedSearchFilter) -> AdvancedSearchFilter) {
        _state.update { current ->
            val filter = transform(current.advancedFilter)
            current.copy(
                advancedFilter = filter,
                filteredCount = current.streams.count {
                    CatalogFilterMatcher.matchesContent(it.rating, it.releaseYear, it.genre, filter)
                }
            )
        }
    }

    /** Returns true only when the category has been applied to the current state. */
    fun selectCategoryById(categoryId: String): Boolean {
        val category = _state.value.categories.firstOrNull { it.categoryId == categoryId } ?: return false
        selectCategory(category)
        return true
    }

    private fun observeStreams(categoryId: String) {
        if (observedCategoryId == categoryId && streamsJob?.isActive == true) return
        observedCategoryId = categoryId
        streamsJob?.cancel()
        streamsJob = viewModelScope.launch {
            _state.update { it.copy(isLoadingStreams = it.streams.isEmpty(), error = null) }
            // Voir VodViewModel : délai jusqu'à la première émission de Room.
            val subscribedAt = System.nanoTime()
            var firstEmission = true
            getSeriesStreamsUseCase(categoryId)
                // Voir VodViewModel : dérivations sorties du dispatcher
                // principal, leur coût suit la taille du catalogue.
                .map { streams -> streams to buildFacets(categoryId, streams) }
                .flowOn(computationDispatcher)
                .collect { (streams, facets) ->
                    if (firstEmission) {
                        firstEmission = false
                        com.cstv.app.di.IptvLog.d(
                            "PERF",
                            "Séries première émission catégorie=$categoryId flux=${streams.size} " +
                                "en ${(System.nanoTime() - subscribedAt) / 1_000_000}ms"
                        )
                    }
                    _state.update { current ->
                        current.copy(
                            streams = streams,
                            isLoadingStreams = false,
                            availableGenres = facets.genres,
                            categoryYearRange = facets.yearRange,
                            filteredCount = facets.filteredCount
                        )
                    }
                    refreshCategoryCounts()
                }
        }
    }

    /**
     * Voir VodViewModel : dérivations du panneau de filtres, mesurées pour
     * pouvoir distinguer sur appareil une base lente d'un calcul lent.
     */
    private fun buildFacets(categoryId: String, streams: List<SeriesStream>): com.cstv.app.domain.model.CatalogFacets {
        // Mode « Tout » : le panneau de filtres n'y est pas accessible, ces
        // dérivations parcourraient tout le catalogue pour rien.
        if (categoryId == com.cstv.app.domain.model.ALL_CATEGORIES_ID) {
            return com.cstv.app.domain.model.CatalogFacets.NONE
        }
        val startedAt = System.nanoTime()
        val facets = com.cstv.app.domain.model.CatalogFacetsBuilder.build(
            items = streams,
            genreOf = { it.genre },
            yearOf = { it.releaseYear },
            ratingOf = { it.rating },
            filter = _state.value.advancedFilter
        )
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        com.cstv.app.di.IptvLog.d(
            "PERF",
            "Séries facettes catégorie=$categoryId flux=${streams.size} genres=${facets.genres.size} en ${elapsedMs}ms"
        )
        return facets
    }

    fun refresh() {
        viewModelScope.launch {
            catalogSyncManager.syncNow(com.cstv.app.domain.sync.SyncTrigger.MANUAL)
        }
    }

    /**
     * Point de passage unique avant la lecture d'un épisode : hors ligne, un
     * épisode non téléchargé n'est pas lancé du tout.
     */
    fun requestPlayback(episodeId: Int, onAllowed: () -> Unit) {
        viewModelScope.launch {
            val contentId = com.cstv.app.domain.model.DownloadedItem.episodeContentId(episodeId)
            when (canPlayContentUseCase(contentId)) {
                com.cstv.app.domain.usecase.PlaybackAvailability.Allowed -> onAllowed()
                com.cstv.app.domain.usecase.PlaybackAvailability.RequiresConnection ->
                    _state.update { it.copy(error = com.cstv.app.presentation.OFFLINE_PLAYBACK_MESSAGE) }
                com.cstv.app.domain.usecase.PlaybackAvailability.RequiresReauthentication ->
                    _state.update { it.copy(error = com.cstv.app.presentation.REAUTHENTICATION_MESSAGE) }
            }
        }
    }

    // Compteurs de la bottom sheet, rafraîchis après chaque sync de streams
    // (le cache local vient potentiellement de changer). Échec silencieux :
    // les compteurs sont un enrichissement, jamais bloquants.
    private fun refreshCategoryCounts() {
        viewModelScope.launch {
            try {
                val counts = getSeriesCategoryCountsUseCase()
                _state.update { it.copy(categoryCounts = counts) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }
    }

    fun selectStreamId(seriesId: Int?) {
        val current = _state.value
        if (seriesId != null && current.selectedStreamId == seriesId &&
            (current.isLoadingDetails || current.selectedSeriesDetails != null)
        ) return

        cancelTrailerPreview()
        ratingObservation?.cancel()
        _state.update { it.copy(selectedStreamId = seriesId, selectedSeriesDetails = null, relatedSeries = emptyList(), mediaRating = null, ratingError = null) }
        if (seriesId != null) {
            ratingObservation = viewModelScope.launch {
                mediaRatingRepository.observeRating(seriesId, RatedMediaType.SERIES).collect { rating ->
                    _state.update { current -> if (current.selectedStreamId == seriesId) current.copy(mediaRating = rating) else current }
                }
            }
            loadSeriesDetails(seriesId)
        }
    }

    fun startTrailerPreview(media: TrailerMedia) {
        if (media == activeTrailerMedia) return
        trailerJob?.cancel(); activeTrailerMedia = media
        _state.update { it.copy(trailerPreview = TrailerPreviewUiState.Preparing) }
        trailerJob = viewModelScope.launch {
            val preview = try { getTrailerPreviewUseCase(media) } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                null
            }
            if (activeTrailerMedia == media) _state.update { it.copy(trailerPreview = preview?.let(TrailerPreviewUiState::Playing) ?: TrailerPreviewUiState.Poster) }
        }
    }

    fun cancelTrailerPreview() {
        trailerJob?.cancel(); trailerJob = null; activeTrailerMedia = null
        _state.update { it.copy(trailerPreview = TrailerPreviewUiState.Poster) }
    }

    fun reportTrailerPlaybackFailure(media: TrailerMedia) {
        if (activeTrailerMedia == media) {
            _state.update { it.copy(trailerPreview = TrailerPreviewUiState.Failed) }
            viewModelScope.launch { invalidateTrailerPreviewUseCase(media) }
        }
    }

    fun setRating(value: MediaRatingValue?) = viewModelScope.launch {
        val details = _state.value.selectedSeriesDetails ?: return@launch
        if (_state.value.isRatingSaving) return@launch
        _state.update { it.copy(isRatingSaving = true, ratingError = null) }
        try {
            setMediaRatingUseCase(details.seriesId, RatedMediaType.SERIES, value, details.episodes.values.flatten().map { it.id }.toSet())
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            _state.update { it.copy(ratingError = "Impossible d'enregistrer votre évaluation. Réessayez.") }
        } finally {
            _state.update { it.copy(isRatingSaving = false) }
        }
    }

    fun consumeRatingError() { _state.update { it.copy(ratingError = null) } }

    fun loadSeriesDetails(seriesId: Int) {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingDetails = true, error = null) }
            try {
                val details = getSeriesDetailsUseCase(seriesId)
                _state.update { it.copy(selectedSeriesDetails = details, isLoadingDetails = false) }
                loadRelatedSeries(details.seriesId, details.genre)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update {
                    it.copy(
                        isLoadingDetails = false,
                        error = com.cstv.app.presentation.mediaLoadErrorMessage(
                            e,
                            "Impossible de charger les détails de la série."
                        )
                    )
                }
            }
        }
    }

    // Suggestions "Titres associés" : échec/vide silencieux, jamais bloquant
    // (dépend de l'enrichissement genre progressif du cache local).
    private fun loadRelatedSeries(seriesId: Int, genre: String?) {
        viewModelScope.launch {
            try {
                val related = getRelatedSeriesUseCase(seriesId, genre)
                _state.update {
                    if (it.selectedSeriesDetails?.seriesId == seriesId) it.copy(relatedSeries = related) else it
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }
    }

    fun savePosition(
        episode: SeriesEpisode,
        positionMs: Long,
        durationMs: Long,
        seriesName: String,
        seriesCover: String?,
        seriesId: Int? = null
    ) {
        viewModelScope.launch {
            // T20: title/cover/extension/season/episode are resolved from the catalogue at
            // display time (series_episodes + series_streams) -- no longer passed here.
            savePlaybackPositionUseCase(
                kind = com.cstv.app.domain.model.MediaKind.EPISODE.storageValue,
                providerId = episode.id,
                positionMs = positionMs,
                durationMs = durationMs
            )
            _state.value.selectedSeriesDetails?.let { currentDetails ->
                loadSeriesDetails(currentDetails.seriesId)
            }
        }
    }

    private fun getSeasonForEpisode(details: SeriesDetails, episodeId: Int): Int {
        for ((seasonNum, episodesList) in details.episodes) {
            if (episodesList.any { it.id == episodeId }) {
                return seasonNum
            }
        }
        return 1
    }

    fun clearPosition(episodeId: Int) {
        viewModelScope.launch {
            savePlaybackPositionUseCase(com.cstv.app.domain.model.MediaKind.EPISODE.storageValue, episodeId, 0L, 0L)
            _state.value.selectedSeriesDetails?.let { currentDetails ->
                loadSeriesDetails(currentDetails.seriesId)
            }
        }
    }

    /** Called only from the player lifecycle: start, pause and natural end. */
    fun markPlaybackForCloud() {
        viewModelScope.launch { markPlaybackSyncUseCase?.invoke() }
    }

    fun getCredentials(): Credentials? {
        return credentialsManager.getCredentials()
    }
}
