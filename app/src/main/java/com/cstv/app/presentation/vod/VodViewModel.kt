package com.cstv.app.presentation.vod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cstv.app.data.local.storage.CredentialsManager
import com.cstv.app.domain.model.Credentials
import com.cstv.app.data.local.storage.SettingsManager
import com.cstv.app.data.local.storage.ResizeMode
import com.cstv.app.domain.model.VodCategory
import com.cstv.app.domain.model.VodDetails
import com.cstv.app.domain.model.VodStream
import com.cstv.app.domain.model.AdvancedSearchFilter
import com.cstv.app.domain.model.CatalogFilterMatcher
import com.cstv.app.domain.usecase.GetVodCategoriesUseCase
import com.cstv.app.domain.usecase.GetVodCategoryCountsUseCase
import com.cstv.app.domain.usecase.GetVodDetailsUseCase
import com.cstv.app.domain.usecase.GetRelatedMoviesUseCase
import com.cstv.app.domain.usecase.GetVodStreamsUseCase
import com.cstv.app.domain.usecase.SavePlaybackPositionUseCase
import com.cstv.app.domain.usecase.RemoveFromContinueWatchingUseCase
import com.cstv.app.domain.model.PlaybackPosition
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.Flow
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

@HiltViewModel
class VodViewModel @Inject constructor(
    private val getVodCategoriesUseCase: GetVodCategoriesUseCase,
    private val getVodCategoryCountsUseCase: GetVodCategoryCountsUseCase,
    private val getVodStreamsUseCase: GetVodStreamsUseCase,
    private val getVodDetailsUseCase: GetVodDetailsUseCase,
    private val getRelatedMoviesUseCase: GetRelatedMoviesUseCase,
    private val savePlaybackPositionUseCase: SavePlaybackPositionUseCase,
    private val credentialsManager: CredentialsManager,
    private val settingsManager: SettingsManager,
    private val trackPreferenceRepository: com.cstv.app.domain.repository.TrackPreferenceRepository,
    private val categoryPreferenceRepository: com.cstv.app.domain.repository.CategoryPreferenceRepository,
    private val vodRepository: com.cstv.app.domain.repository.VodRepository,
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
    private val markPlaybackSyncUseCase: com.cstv.app.domain.usecase.MarkPlaybackSyncUseCase? = null,
    private val requestPlaybackLockUseCase: com.cstv.app.domain.usecase.PlaybackLockRequester? = null,
    private val releasePlaybackLockUseCase: com.cstv.app.domain.usecase.PlaybackLockReleaser? = null,
    private val playbackLockManager: com.cstv.app.data.playback.PlaybackLockManager? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(VodState())
    val state: StateFlow<VodState> = _state.asStateFlow()
    private val _playbackLockUiState = MutableStateFlow(com.cstv.app.domain.model.PlaybackLockUiState())
    val playbackLockUiState: StateFlow<com.cstv.app.domain.model.PlaybackLockUiState> = _playbackLockUiState.asStateFlow()
    private var playbackLockRequest: kotlinx.coroutines.Job? = null
    private var playbackLockContentId: String? = null

    init {
        playbackLockManager?.let { manager -> viewModelScope.launch {
            manager.state.collect { lock ->
                if (lock is com.cstv.app.domain.model.PlaybackLockState.Revoked) _playbackLockUiState.value = _playbackLockUiState.value.copy(canStartPlayback = false, conflict = null, takenOverBy = lock.holder)
            }
        } }
    }

    fun beginPlaybackLock(contentId: String) { playbackLockContentId = contentId; requestPlaybackLock(false) }
    fun takeOverPlaybackLock() = requestPlaybackLock(true)
    fun resumePlaybackLock() = requestPlaybackLock(false)
    fun cancelPlaybackLockRequest() { _playbackLockUiState.value = _playbackLockUiState.value.copy(conflict = null) }
    fun pausePlaybackLock() { releasePlaybackLockUseCase?.release(); _playbackLockUiState.value = _playbackLockUiState.value.copy(canStartPlayback = false) }
    fun releasePlaybackLock() = pausePlaybackLock()
    private fun requestPlaybackLock(takeover: Boolean) {
        playbackLockRequest?.cancel()
        playbackLockRequest = viewModelScope.launch {
            when (val result = requestPlaybackLockUseCase?.request(playbackLockContentId, takeover) ?: com.cstv.app.domain.usecase.PlaybackLockRequestResult.Allowed) {
                com.cstv.app.domain.usecase.PlaybackLockRequestResult.Allowed, com.cstv.app.domain.usecase.PlaybackLockRequestResult.NotRequired -> _playbackLockUiState.value = com.cstv.app.domain.model.PlaybackLockUiState(canStartPlayback = true, failOpen = playbackLockManager?.state?.value is com.cstv.app.domain.model.PlaybackLockState.FailOpen)
                is com.cstv.app.domain.usecase.PlaybackLockRequestResult.Held -> _playbackLockUiState.value = com.cstv.app.domain.model.PlaybackLockUiState(conflict = result.holder)
            }
        }
    }

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
    val pagedStreams: Flow<PagingData<VodStream>> = state
        .map { it.selectedCategory?.categoryId }
        .distinctUntilChanged()
        .flatMapLatest { categoryId ->
            if (categoryId != null) {
                vodRepository.getVodStreamsPaged(categoryId)
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
                val hiddenVod = try {
                    categoryPreferenceRepository.getPreferences(com.cstv.app.domain.model.CategoryType.VOD)
                        .filterValues { it.hidden }
                        .keys
                } catch (e: Exception) {
                    emptySet()
                }

                val moviesPositions = allPositions.filter { pos ->
                    pos.type == "movie" && pos.positionMs > 0 && pos.positionMs < (pos.durationMs - 15000L)
                }

                // La catégorie d'une reprise est mémorisée sur la position
                // elle-même (T9, MIGRATION_22_23), résolue une fois à
                // l'enregistrement. La reconstruire ici imposait de relire les
                // 39 000 lignes du catalogue — `SELECT *`, colonnes lourdes
                // comprises — à chaque entrée sur l'écran, en concurrence avec
                // la requête qui alimente justement la liste affichée. Voir
                // `HomeViewModel.groupResumeWatching`, qui lit déjà ce champ.
                val filtered = moviesPositions.filter { pos ->
                    val catId = pos.categoryId
                    catId == null || catId !in hiddenVod
                }

                _state.update { it.copy(resumeMovies = filtered) }
            }
        }
    }

    // --- Préférence de pistes globale (fallback "dernière langue utilisée") ---
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

    // --- Préférence de pistes par film (Phase 29, clé = streamId, profil actif) ---
    suspend fun getMovieTrackPreference(streamId: Int) =
        trackPreferenceRepository.getPreference(com.cstv.app.domain.model.MediaType.MOVIE, streamId)

    /** Sauvegarde l'audio choisi pour ce film ET met à jour le fallback global. */
    fun saveMovieAudio(streamId: Int, lang: String?) {
        settingsManager.setPreferredAudio(lang)
        viewModelScope.launch {
            trackPreferenceRepository.saveAudioLang(com.cstv.app.domain.model.MediaType.MOVIE, streamId, lang)
        }
    }

    /** Sauvegarde les sous-titres choisis pour ce film ET le fallback global. */
    fun saveMovieSubtitle(streamId: Int, lang: String?) {
        settingsManager.setPreferredSubtitle(lang)
        viewModelScope.launch {
            trackPreferenceRepository.saveSubtitleLang(com.cstv.app.domain.model.MediaType.MOVIE, streamId, lang)
        }
    }

    fun getSubtitleStyle() = settingsManager.getSubtitleStyle()

    fun getResizeMode() = settingsManager.getResizeMode()
    fun setResizeMode(mode: ResizeMode) {
        settingsManager.setResizeMode(mode)
    }

    /**
     * Les catégories viennent de Room et sont ré-émises à chaque écriture de
     * synchronisation : les listes déjà affichées se mettent à jour sans
     * réinitialiser la navigation ni le profil actif.
     */
    private fun observeCategories() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingCategories = it.categories.isEmpty()) }
            // Le voile de chargement est levé par `isLoadingCategories` autant
            // que par `isLoadingStreams` : mesuré séparément, sans quoi une
            // liste de catégories lente se lit à l'écran comme une liste de
            // flux lente.
            val subscribedAt = System.nanoTime()
            var firstEmission = true
            getVodCategoriesUseCase().collect { categories ->
                if (firstEmission) {
                    firstEmission = false
                    com.cstv.app.di.IptvLog.d(
                        "PERF",
                        "VOD première émission catégories=${categories.size} " +
                            "en ${(System.nanoTime() - subscribedAt) / 1_000_000}ms"
                    )
                }
                val finalCategories = listOf(VodCategory("all", "Tout", 0)) + categories
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
     * T7 (D3) : entrer sur l'onglet VOD déclenche silencieusement une
     * synchronisation si le catalogue est périmé — pas d'erreur, pas de
     * bannière, pas d'action manuelle. `syncIfStale()` est déjà un no-op si le
     * catalogue est frais, hors ligne, ou une synchronisation est en cours.
     */
    private fun triggerSilentSyncIfStale() {
        viewModelScope.launch {
            runCatching { catalogSyncManager.syncIfStale() }
        }
    }

    fun selectCategory(category: VodCategory) {
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
            // Chargement affiché uniquement tant que Room n'a rien émis : une
            // synchronisation en cours n'a plus le droit de bloquer l'écran.
            _state.update { it.copy(isLoadingStreams = it.streams.isEmpty(), error = null) }
            // Délai jusqu'à la première émission de Room : sépare une requête
            // lente d'un rendu lent, les deux se manifestant à l'écran par le
            // même onglet « Tout » qui tarde à apparaître.
            val subscribedAt = System.nanoTime()
            var firstEmission = true
            getVodStreamsUseCase(categoryId)
                // Les dérivations parcourent tout le catalogue sur l'onglet
                // « Tout » : laissées sur le dispatcher principal, elles
                // retardaient d'autant le premier rendu de la liste.
                .map { streams -> streams to buildFacets(categoryId, streams) }
                .flowOn(computationDispatcher)
                .collect { (streams, facets) ->
                    if (firstEmission) {
                        firstEmission = false
                        com.cstv.app.di.IptvLog.d(
                            "PERF",
                            "VOD première émission catégorie=$categoryId flux=${streams.size} " +
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
     * Dérivations du panneau de filtres, mesurées : c'est le poste de coût qui
     * décide de la latence d'affichage de l'onglet « Tout ». Le journal permet
     * de trancher sur appareil entre une base lente (`Room`) et un calcul lent
     * (`CatalogFacetsBuilder`), les deux se présentant à l'écran de la même
     * façon.
     */
    private fun buildFacets(categoryId: String, streams: List<VodStream>): com.cstv.app.domain.model.CatalogFacets {
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
            "VOD facettes catégorie=$categoryId flux=${streams.size} genres=${facets.genres.size} en ${elapsedMs}ms"
        )
        return facets
    }

    /**
     * Actualisation explicite. Le catalogue local reste servi pendant la
     * synchronisation, sans commentaire à l'écran.
     */
    fun refresh() {
        viewModelScope.launch {
            catalogSyncManager.syncNow(com.cstv.app.domain.sync.SyncTrigger.MANUAL)
        }
    }

    /**
     * Point de passage unique avant toute lecture d'un film : hors ligne, un
     * flux non téléchargé n'est pas lancé du tout, plutôt que de laisser
     * ExoPlayer échouer avec une erreur brute.
     */
    fun requestPlayback(streamId: Int, onAllowed: () -> Unit) {
        viewModelScope.launch {
            val contentId = com.cstv.app.domain.model.DownloadedItem.movieContentId(streamId)
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
                // `GROUP BY categoryId` sur tout le catalogue, relancé après
                // chaque émission de la liste : mesuré pour vérifier qu'il
                // reste bien servi par l'index et ne redevient pas un parcours
                // de table.
                val startedAt = System.nanoTime()
                val counts = getVodCategoryCountsUseCase()
                com.cstv.app.di.IptvLog.d(
                    "PERF",
                    "VOD compteurs catégories=${counts.size} en ${(System.nanoTime() - startedAt) / 1_000_000}ms"
                )
                _state.update { it.copy(categoryCounts = counts) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }
    }

    fun selectStreamId(streamId: Int?) {
        val current = _state.value
        if (streamId != null && current.selectedStreamId == streamId &&
            (current.isLoadingDetails || current.selectedVodDetails != null)
        ) return

        cancelTrailerPreview()
        ratingObservation?.cancel()
        _state.update { it.copy(selectedStreamId = streamId, selectedVodDetails = null, relatedStreams = emptyList(), mediaRating = null, ratingError = null) }
        if (streamId != null) {
            ratingObservation = viewModelScope.launch {
                mediaRatingRepository.observeRating(streamId, RatedMediaType.MOVIE).collect { rating ->
                    _state.update { current -> if (current.selectedStreamId == streamId) current.copy(mediaRating = rating) else current }
                }
            }
            loadVodDetails(streamId)
        }
    }

    /**
     * Démarre la résolution uniquement à la demande de la couche UI. Toute
     * réponse arrivée après un changement de fiche est volontairement ignorée.
     */
    fun startTrailerPreview(media: TrailerMedia) {
        if (media == activeTrailerMedia) return
        trailerJob?.cancel()
        activeTrailerMedia = media
        _state.update { it.copy(trailerPreview = TrailerPreviewUiState.Preparing) }
        trailerJob = viewModelScope.launch {
            val preview = try {
                getTrailerPreviewUseCase(media)
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                null
            }
            if (activeTrailerMedia == media) {
                _state.update {
                    it.copy(
                        trailerPreview = preview?.let(TrailerPreviewUiState::Playing)
                            ?: TrailerPreviewUiState.Poster
                    )
                }
            }
        }
    }

    /** Annule l'aperçu dès que la fiche n'est plus le contexte actif. */
    fun cancelTrailerPreview() {
        trailerJob?.cancel()
        trailerJob = null
        activeTrailerMedia = null
        _state.update { it.copy(trailerPreview = TrailerPreviewUiState.Poster) }
    }

    /** Un échec de lecture n'affecte que le trailer encore affiché. */
    fun reportTrailerPlaybackFailure(media: TrailerMedia) {
        if (activeTrailerMedia == media) {
            _state.update { it.copy(trailerPreview = TrailerPreviewUiState.Failed) }
            viewModelScope.launch { invalidateTrailerPreviewUseCase(media) }
        }
    }

    fun setRating(value: MediaRatingValue?) = viewModelScope.launch {
        val streamId = _state.value.selectedVodDetails?.streamId ?: return@launch
        if (_state.value.isRatingSaving) return@launch
        _state.update { it.copy(isRatingSaving = true, ratingError = null) }
        try {
            setMediaRatingUseCase(streamId, RatedMediaType.MOVIE, value)
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            _state.update { it.copy(ratingError = "Impossible d'enregistrer votre évaluation. Réessayez.") }
        } finally {
            _state.update { it.copy(isRatingSaving = false) }
        }
    }

    fun consumeRatingError() { _state.update { it.copy(ratingError = null) } }

    private fun loadVodDetails(streamId: Int) {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingDetails = true, error = null) }
            try {
                val details = getVodDetailsUseCase(streamId)
                _state.update { it.copy(selectedVodDetails = details, isLoadingDetails = false) }
                loadRelatedMovies(details.streamId, details.genre)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update {
                    it.copy(
                        isLoadingDetails = false,
                        error = com.cstv.app.presentation.mediaLoadErrorMessage(
                            e,
                            "Impossible de charger les détails du film."
                        )
                    )
                }
            }
        }
    }

    // Suggestions "Titres associés" : échec/vide silencieux, jamais bloquant
    // (dépend de l'enrichissement genre progressif du cache local).
    private fun loadRelatedMovies(streamId: Int, genre: String?) {
        viewModelScope.launch {
            try {
                val related = getRelatedMoviesUseCase(streamId, genre)
                _state.update {
                    if (it.selectedVodDetails?.streamId == streamId) it.copy(relatedStreams = related) else it
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }
    }

    fun savePosition(streamId: Int, positionMs: Long, durationMs: Long, details: VodDetails) {
        viewModelScope.launch {
            // T20: title/cover/extension are resolved from the catalogue (vod_streams) at display
            // time -- no longer passed here.
            savePlaybackPositionUseCase(
                kind = com.cstv.app.domain.model.MediaKind.MOVIE.storageValue,
                providerId = streamId,
                positionMs = positionMs,
                durationMs = durationMs
            )
            _state.update {
                val currentDetails = it.selectedVodDetails
                if (currentDetails != null && currentDetails.streamId == streamId) {
                    it.copy(selectedVodDetails = currentDetails.copy(resumePositionMs = positionMs, durationMs = durationMs))
                } else it
            }
        }
    }

    fun clearPosition(streamId: Int) {
        viewModelScope.launch {
            savePlaybackPositionUseCase(com.cstv.app.domain.model.MediaKind.MOVIE.storageValue, streamId, 0L, 0L)
            _state.value.selectedVodDetails?.let { currentDetails ->
                if (currentDetails.streamId == streamId) {
                    _state.update { 
                        it.copy(selectedVodDetails = currentDetails.copy(resumePositionMs = 0L, durationMs = 0L))
                    }
                }
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
