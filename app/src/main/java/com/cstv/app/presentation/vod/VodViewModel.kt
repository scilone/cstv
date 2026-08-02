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
    private val invalidateTrailerPreviewUseCase: com.cstv.app.domain.usecase.InvalidateTrailerPreviewUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(VodState())
    val state: StateFlow<VodState> = _state.asStateFlow()

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

                val vodMap = try {
                    vodRepository.getCachedVodStreams("all").associate { it.streamId to it.categoryId }
                } catch (e: Exception) {
                    emptyMap()
                }

                val filtered = moviesPositions.filter { pos ->
                    val catId = vodMap[pos.streamId]
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
            getVodCategoriesUseCase().collect { categories ->
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
        _state.update { it.copy(selectedCategory = category, streams = emptyList()) }
        observeStreams(category.categoryId)
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
            getVodStreamsUseCase(categoryId).collect { streams ->
                _state.update { it.copy(streams = streams, isLoadingStreams = false) }
                refreshCategoryCounts()
            }
        }
    }

    /**
     * Actualisation explicite. Le catalogue local reste servi pendant la
     * synchronisation ; seul l'indicateur de la bannière en rend compte.
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
                val counts = getVodCategoryCountsUseCase()
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
            val title = details.name
            val coverUrl = details.coverBig
            val type = "movie"
            val containerExtension = details.containerExtension

            savePlaybackPositionUseCase(
                streamId = streamId,
                positionMs = positionMs,
                durationMs = durationMs,
                title = title,
                coverUrl = coverUrl,
                type = type,
                containerExtension = containerExtension
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
            savePlaybackPositionUseCase(streamId, 0L, 0L)
            _state.value.selectedVodDetails?.let { currentDetails ->
                if (currentDetails.streamId == streamId) {
                    _state.update { 
                        it.copy(selectedVodDetails = currentDetails.copy(resumePositionMs = 0L, durationMs = 0L))
                    }
                }
            }
        }
    }

    fun getCredentials(): Credentials? {
        return credentialsManager.getCredentials()
    }
}
