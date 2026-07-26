package com.cstv.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cstv.app.domain.model.PlaybackPosition
import com.cstv.app.domain.model.FavoriteItem
import com.cstv.app.domain.model.LiveCategory
import com.cstv.app.domain.model.LiveStream
import com.cstv.app.domain.model.VodStream
import com.cstv.app.domain.model.SeriesStream
import com.cstv.app.domain.model.CategoryType
import com.cstv.app.domain.model.DownloadedItem
import com.cstv.app.domain.model.DownloadStatus
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import com.cstv.app.domain.repository.DownloadRepository
import com.cstv.app.domain.repository.FavoritesRepository
import com.cstv.app.domain.repository.LiveTvRepository
import com.cstv.app.domain.repository.SeriesRepository
import com.cstv.app.domain.repository.VodRepository
import com.cstv.app.data.local.storage.ProfileManager
import com.cstv.app.domain.usecase.GetLiveCategoriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.cstv.app.domain.model.LiveEpgProgram
import com.cstv.app.domain.usecase.GetLiveEpgUseCase
import com.cstv.app.domain.usecase.GetRecommendationsUseCase
import com.cstv.app.domain.usecase.GetPopularTop10InCatalogUseCase
import com.cstv.app.domain.usecase.GetTrailerPreviewUseCase
import com.cstv.app.domain.model.TrailerMedia
import com.cstv.app.domain.model.TrailerPreview
import com.cstv.app.domain.model.TrendingCatalogItem
import kotlinx.coroutines.Job
import com.cstv.app.domain.model.TopRatedSelector

private const val EPG_POLL_INTERVAL_MILLIS = 60_000L

sealed interface TrailerPreviewUiState {
    data object Poster : TrailerPreviewUiState
    data object Preparing : TrailerPreviewUiState
    data class Playing(val preview: TrailerPreview) : TrailerPreviewUiState
    data object Failed : TrailerPreviewUiState
}

data class HomeState(
    val isLoading: Boolean = false,
    val resumeWatchingList: List<PlaybackPosition> = emptyList(),
    val favoritesList: List<FavoriteItem> = emptyList(),
    val trendingList: List<com.cstv.app.domain.model.TrendingCatalogItem> = emptyList(),
    
    val firstLiveCategory: LiveCategory? = null,
    val firstLiveStreams: List<LiveStream> = emptyList(),
    
    val firstVodStreams: List<VodStream> = emptyList(),
    val firstSeriesStreams: List<SeriesStream> = emptyList(),
    
    val topVodStreams: List<VodStream> = emptyList(),
    val topSeriesStreams: List<SeriesStream> = emptyList(),
    val popularTopVodStreams: List<VodStream>? = null,
    val popularTopSeriesStreams: List<SeriesStream>? = null,
    
    val recommendedMovies: List<VodStream> = emptyList(),
    val recommendedSeries: List<SeriesStream> = emptyList(),
    val downloadedItems: List<DownloadedItem> = emptyList(),
    
    val error: String? = null,
    val epgPrograms: Map<Int, LiveEpgProgram> = emptyMap(),
    val isRemovingHistory: Boolean = false,
    val historyRemovalError: String? = null,
    val trailerPreview: TrailerPreviewUiState = TrailerPreviewUiState.Poster
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val vodRepository: VodRepository,
    private val liveTvRepository: LiveTvRepository,
    private val seriesRepository: SeriesRepository,
    private val favoritesRepository: FavoritesRepository,
    private val downloadRepository: DownloadRepository,
    private val getLiveEpgUseCase: GetLiveEpgUseCase,
    private val getLiveCategoriesUseCase: GetLiveCategoriesUseCase,
    private val categoryPreferenceRepository: CategoryPreferenceRepository,
    private val getTrendingInCatalogUseCase: com.cstv.app.domain.usecase.GetTrendingInCatalogUseCase,
    private val getRecommendationsUseCase: GetRecommendationsUseCase,
    private val getPopularTop10InCatalogUseCase: GetPopularTop10InCatalogUseCase,
    private val removeFromContinueWatchingUseCase: com.cstv.app.domain.usecase.RemoveFromContinueWatchingUseCase,
    private val getTrailerPreviewUseCase: GetTrailerPreviewUseCase,
    private val profileManager: ProfileManager,
    private val canPlayContentUseCase: com.cstv.app.domain.usecase.CanPlayContentUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private val scrollPositions = mutableMapOf<String, Pair<Int, Int>>()
    private var trailerJob: Job? = null
    private var activeTrailerMedia: TrailerMedia? = null
    private var popularJob: Job? = null
    private var trendingJob: Job? = null
    private var catalogJob: Job? = null
    private var recommendationsJob: Job? = null
    private var hasActiveProfile = false

    /**
     * Point de passage unique avant toute lecture lancée depuis l'Accueil
     * (« Continuer à regarder », rangées de contenus, chaîne Live). Hors ligne,
     * un flux non téléchargé n'est pas lancé : le player n'a jamais à échouer
     * avec une erreur brute.
     *
     * [contentId] suit la convention des téléchargements (`movie_123`,
     * `episode_456`) ; `null` pour un flux Live, jamais téléchargeable.
     */
    fun requestPlayback(contentId: String?, onAllowed: () -> Unit) {
        viewModelScope.launch {
            when (canPlayContentUseCase(contentId)) {
                com.cstv.app.domain.usecase.PlaybackAvailability.Allowed -> onAllowed()
                com.cstv.app.domain.usecase.PlaybackAvailability.RequiresConnection ->
                    _state.update { it.copy(error = com.cstv.app.presentation.OFFLINE_PLAYBACK_MESSAGE) }
                com.cstv.app.domain.usecase.PlaybackAvailability.RequiresReauthentication ->
                    _state.update { it.copy(error = com.cstv.app.presentation.REAUTHENTICATION_MESSAGE) }
            }
        }
    }

    fun selectTrendingPreview(item: TrendingCatalogItem?) {
        val media = item?.toTrailerMedia()
        if (media == activeTrailerMedia) return
        trailerJob?.cancel()
        activeTrailerMedia = media
        if (media == null) {
            _state.update { it.copy(trailerPreview = TrailerPreviewUiState.Poster) }
            return
        }
        _state.update { it.copy(trailerPreview = TrailerPreviewUiState.Preparing) }
        trailerJob = viewModelScope.launch {
            val preview = try {
                getTrailerPreviewUseCase(media)
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                null
            }
            // A stale response must never replace the newly active pager item.
            if (activeTrailerMedia == media) {
                _state.update {
                    it.copy(trailerPreview = preview?.let(TrailerPreviewUiState::Playing) ?: TrailerPreviewUiState.Poster)
                }
            }
        }
    }

    fun cancelTrendingPreview() {
        trailerJob?.cancel()
        trailerJob = null
        activeTrailerMedia = null
        _state.update { it.copy(trailerPreview = TrailerPreviewUiState.Poster) }
    }

    /** The embedded player failed after a source was resolved: keep the poster silently. */
    fun reportTrailerPlaybackFailure(media: TrailerMedia) {
        if (activeTrailerMedia == media) {
            _state.update { it.copy(trailerPreview = TrailerPreviewUiState.Failed) }
        }
    }

    private fun TrendingCatalogItem.toTrailerMedia(): TrailerMedia? = when {
        matchedMovie != null -> TrailerMedia.Movie(matchedMovie.streamId, trendingTitle.tmdbId)
        matchedSeries != null -> TrailerMedia.Series(matchedSeries.seriesId, trendingTitle.tmdbId)
        else -> null
    }

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

    // Guards against duplicate concurrent fetches and hammering channels without EPG data.
    // Doivent être initialisés AVANT le bloc init{} : viewModelScope utilise
    // Dispatchers.Main.immediate, qui exécute la coroutine de loadHomeData() en
    // ligne (pas de redispatch) si on est déjà sur son thread — y compris
    // pendant l'exécution du bloc init{} lui-même. refreshVisibleEpg()
    // (appelée en fin de loadHomeData) accède donc à ces champs alors que la
    // construction de l'objet n'est pas terminée : s'ils étaient déclarés
    // après init{} (ordre d'exécution des initialisers Kotlin = ordre
    // textuel), ils seraient encore `null` à cet instant -> NPE, capturée
    // silencieusement par le catch(Exception) de loadHomeData.
    private val epgInFlight = mutableSetOf<Int>()
    private val epgLastAttempt = mutableMapOf<Int, Long>()

    init {
        viewModelScope.launch {
            getRecommendationsUseCase.invalidations.collect { refreshRecommendations() }
        }
        viewModelScope.launch {
            var isInitialProfile = true
            // B12: activeProfileId is the sole initial-load trigger. Keeping a
            // direct loadHomeData() call here would start two loads at launch.
            profileManager.activeProfileId
                .filter { it != ProfileManager.NO_PROFILE }
                .distinctUntilChanged()
                .collect {
                    hasActiveProfile = true
                    loadHomeData(resetVisibleContent = !isInitialProfile)
                    isInitialProfile = false
                }
        }
        // Phase 58 : recharge la Home quand les préférences de catégories
        // (masquage/ordre) changent — le ViewModel est partagé au niveau app.
        viewModelScope.launch {
            categoryPreferenceRepository.changes.collect {
                if (hasActiveProfile) loadHomeData()
            }
        }
        // Phase 41 : "Continuer à regarder" et "Favoris" restent à jour en
        // continu (ajout/suppression d'un favori ailleurs dans l'app, reprise
        // de lecture) sans reload manuel ni ré-écoute du changement de profil
        // (déjà géré par le flatMapLatest des repositories sur activeProfileId).
        // Phase 58 : Combiné avec les changements de préférences de catégories pour
        // masquer immédiatement les replays/favoris issus de catégories masquées.
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                vodRepository.observeAllPlaybackPositions(),
                categoryPreferenceRepository.changes.onStart { emit(Unit) }
            ) { allPositions, _ ->
                allPositions
            }.collect { allPositions ->
                val hiddenVod = hiddenCategoryIds(CategoryType.VOD)
                val hiddenSeries = hiddenCategoryIds(CategoryType.SERIES)
                val vodMap = try {
                    vodRepository.getCachedVodStreams("all").associate { it.streamId to it.categoryId }
                } catch (e: Exception) {
                    emptyMap()
                }
                val seriesMap = try {
                    seriesRepository.getCachedSeriesStreams("all").associate { it.seriesId to it.categoryId }
                } catch (e: Exception) {
                    emptyMap()
                }

                _state.update {
                    it.copy(
                        resumeWatchingList = groupResumeWatching(
                            allPositions = allPositions,
                            hiddenVodCategories = hiddenVod,
                            hiddenSeriesCategories = hiddenSeries,
                            vodStreamCategoryMap = vodMap,
                            seriesStreamCategoryMap = seriesMap
                        )
                    )
                }
            }
        }
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                favoritesRepository.observeFavorites(),
                categoryPreferenceRepository.changes.onStart { emit(Unit) }
            ) { favorites, _ ->
                favorites
            }.collect { favorites ->
                val hiddenVod = hiddenCategoryIds(CategoryType.VOD)
                val hiddenSeries = hiddenCategoryIds(CategoryType.SERIES)
                val hiddenLive = hiddenCategoryIds(CategoryType.LIVE)

                val filteredFavorites = favorites.filter { fav ->
                    when (fav.type) {
                        "movie", "vod" -> fav.categoryId !in hiddenVod
                        "series" -> fav.categoryId !in hiddenSeries
                        "live" -> fav.categoryId !in hiddenLive
                        else -> true
                    }
                }
                _state.update { it.copy(favoritesList = filteredFavorites) }
            }
        }
        // F15: le DAO émet aussi pendant la progression des téléchargements en cours.
        // Ne publier que la liste visible évite de recomposer l'Accueil à chaque tick.
        viewModelScope.launch {
            downloadRepository.observeDownloads()
                .map { items -> items.filter { it.status == DownloadStatus.COMPLETED } }
                .distinctUntilChanged()
                .collect { completed ->
                    _state.update { it.copy(downloadedItems = completed.take(20)) }
                }
        }
        // Phase 42 : un seul ticker pour toute la rangée "TV" au lieu d'une
        // boucle while(true)+delay(60s) par carte visible (une par chaîne).
        // Le premier passage a lieu juste après le chargement initial de
        // firstLiveStreams (voir loadHomeData) ; ce ticker ne fait que les
        // rafraîchissements périodiques suivants.
        viewModelScope.launch {
            while (true) {
                delay(EPG_POLL_INTERVAL_MILLIS)
                refreshVisibleEpg()
            }
        }
    }

    private fun refreshVisibleEpg() {
        _state.value.firstLiveStreams.forEach { loadEpgForStream(it.streamId) }
    }

    // Regroupe les épisodes de série par seriesId (Phase 30) : une seule
    // carte par série, celle du dernier épisode vu. allPositions est déjà
    // trié par lastAccessedAt DESC (VodDao.observeAllPlaybackPositions), donc
    // le premier épisode rencontré par seriesId est bien le plus récent.
    // Les films (seriesId == null) ne sont pas regroupés.
    // Filtré également par catégories masquées (Phase 58).
    private fun groupResumeWatching(
        allPositions: List<PlaybackPosition>,
        hiddenVodCategories: Set<String>,
        hiddenSeriesCategories: Set<String>,
        vodStreamCategoryMap: Map<Int, String>,
        seriesStreamCategoryMap: Map<Int, String>
    ): List<PlaybackPosition> {
        val resumeWatchingRaw = allPositions.filter { pos ->
            pos.positionMs > 0 && pos.positionMs < (pos.durationMs - 15000L)
        }
        val filtered = resumeWatchingRaw.filter { pos ->
            if (pos.seriesId != null) {
                val catId = seriesStreamCategoryMap[pos.seriesId]
                catId == null || catId !in hiddenSeriesCategories
            } else {
                val catId = vodStreamCategoryMap[pos.streamId]
                catId == null || catId !in hiddenVodCategories
            }
        }
        val seenSeriesIds = mutableSetOf<Int>()
        return filtered.filter { pos ->
            val seriesId = pos.seriesId
            if (seriesId == null) true else seenSeriesIds.add(seriesId)
        }
    }

    fun loadEpgForStream(streamId: Int) {
        val now = System.currentTimeMillis()
        val current = _state.value.epgPrograms[streamId]
        if (current != null && now / 1000L < current.endTimestamp) {
            return
        }
        if (streamId in epgInFlight || now - (epgLastAttempt[streamId] ?: 0L) < 60_000L) {
            return
        }
        epgInFlight.add(streamId)
        epgLastAttempt[streamId] = now

        viewModelScope.launch {
            try {
                val program = getLiveEpgUseCase(streamId)
                if (program != null) {
                    _state.update {
                        it.copy(epgPrograms = it.epgPrograms + (streamId to program))
                    }
                }
            } finally {
                epgInFlight.remove(streamId)
            }
        }
    }

    private suspend fun hiddenCategoryIds(type: CategoryType): Set<String> {
        return try {
            categoryPreferenceRepository.getPreferences(type)
                .filterValues { it.hidden }
                .keys
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            emptySet()
        }
    }

    fun loadHomeData(resetVisibleContent: Boolean = false) {
        // A result calculated with a previous profile's hidden categories must
        // never be allowed to repopulate Home after the visible state is purged.
        popularJob?.cancel()
        trendingJob?.cancel()
        catalogJob?.cancel()
        recommendationsJob?.cancel()
        if (resetVisibleContent) {
            _state.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    firstLiveCategory = null,
                    firstLiveStreams = emptyList(),
                    firstVodStreams = emptyList(),
                    firstSeriesStreams = emptyList(),
                    topVodStreams = emptyList(),
                    topSeriesStreams = emptyList(),
                    recommendedMovies = emptyList(),
                    recommendedSeries = emptyList(),
                    trendingList = emptyList(),
                    epgPrograms = emptyMap(),
                    trailerPreview = TrailerPreviewUiState.Poster
                )
            }
            cancelTrendingPreview()
        }
        _state.update { it.copy(popularTopVodStreams = null, popularTopSeriesStreams = null) }

        // F9 : Popular est isolé du chargement local afin que le fallback soit
        // immédiatement disponible et que TMDB ne puisse jamais bloquer Home.
        popularJob = viewModelScope.launch {
            val result = try {
                kotlinx.coroutines.withTimeoutOrNull(15_000L) {
                    getPopularTop10InCatalogUseCase()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                null
            }
            if (result != null) {
                _state.update {
                    it.copy(
                        popularTopVodStreams = result.movies,
                        popularTopSeriesStreams = result.series
                    )
                }
            }
        }

        // Tendances TMDB (F1) : appel réseau externe potentiellement lent/instable
        // (DNS, timeout). Découplé du chargement principal (Live/VOD/Séries, tout
        // local) pour ne JAMAIS bloquer le reste de la Home ni le spinner
        // isLoading — sinon un TMDB lent/hors-ligne fait paraître la Home comme
        // chargeant indéfiniment. Timeout client dur en garde-fou supplémentaire
        // (au cas où un appel suspendu ignorerait les timeouts OkHttp).
        trendingJob = viewModelScope.launch {
            val trendingList = try {
                kotlinx.coroutines.withTimeoutOrNull(15_000L) {
                    getTrendingInCatalogUseCase()
                } ?: emptyList()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                emptyList()
            }
            _state.update { it.copy(trendingList = trendingList) }
        }

        // Recommandations F-6 : calcul intensif (parsing genres sur des milliers d'entrées)
        // Découplé de la Home pour un affichage asynchrone progressif, comme TMDB.
        refreshRecommendations()

        catalogJob = viewModelScope.launch {
            val isCurrentStateEmpty = _state.value.firstLiveStreams.isEmpty() &&
                    _state.value.firstVodStreams.isEmpty() &&
                    _state.value.firstSeriesStreams.isEmpty()
            _state.update { it.copy(isLoading = isCurrentStateEmpty, error = null) }
            try {
                // "Continuer à regarder" et "Favoris" sont alimentés en continu par les
                // Flow collectés dans init() (voir groupResumeWatching) : plus de fetch
                // ponctuel ici.

                // 3. Fetch TV - First Live Category and its Streams
                // (catégories filtrées/ordonnées selon les préférences du profil, Phase 58)
                val liveCategories = try {
                    getLiveCategoriesUseCase.once()
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    emptyList()
                }
                val firstLiveCat = liveCategories.firstOrNull()
                val firstLiveStreams = if (firstLiveCat != null) {
                    try {
                        liveTvRepository.getCachedLiveStreams(firstLiveCat.categoryId)
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        emptyList()
                    }
                } else emptyList()

                // 4. Fetch Movies - Latest additions (toutes catégories confondues)
                val allVodStreams = try {
                    vodRepository.getCachedVodStreams("all")
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    emptyList()
                }
                val hiddenVodCategories = hiddenCategoryIds(CategoryType.VOD)
                val filteredVodStreams = allVodStreams.filter { it.categoryId !in hiddenVodCategories }
                val firstVodStreams = filteredVodStreams
                    .sortedByDescending { it.added?.toLongOrNull() ?: 0L }
                    .take(20)

                val topVodStreams = TopRatedSelector.selectTop10(
                    items = filteredVodStreams,
                    ratingExtractor = { it.rating },
                    addedExtractor = { it.added }
                )

                // 5. Fetch Series - Latest additions (toutes catégories confondues)
                val allSeriesStreams = try {
                    seriesRepository.getCachedSeriesStreams("all")
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    emptyList()
                }
                val hiddenSeriesCategories = hiddenCategoryIds(CategoryType.SERIES)
                val filteredSeriesStreams = allSeriesStreams.filter { it.categoryId !in hiddenSeriesCategories }
                val firstSeriesStreams = filteredSeriesStreams
                    .sortedByDescending { it.added?.toLongOrNull() ?: 0L }
                    .take(20)

                val topSeriesStreams = TopRatedSelector.selectTop10(
                    items = filteredSeriesStreams,
                    ratingExtractor = { it.rating },
                    addedExtractor = { it.added }
                )

                _state.update {
                    it.copy(
                        isLoading = false,
                        firstLiveCategory = firstLiveCat,
                        firstLiveStreams = firstLiveStreams,
                        firstVodStreams = firstVodStreams,
                        firstSeriesStreams = firstSeriesStreams,
                        topVodStreams = topVodStreams,
                        topSeriesStreams = topSeriesStreams
                    )
                }
                refreshVisibleEpg()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Une erreur est survenue lors du chargement de l'accueil."
                    )
                }
            }
        }
    }

    private fun refreshRecommendations() {
        recommendationsJob?.cancel()
        recommendationsJob = viewModelScope.launch {
        try {
            val recos = getRecommendationsUseCase()
            _state.update { it.copy(recommendedMovies = recos.movies, recommendedSeries = recos.series) }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
        }
        }
    }
}
