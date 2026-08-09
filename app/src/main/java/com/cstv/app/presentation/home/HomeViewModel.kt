package com.cstv.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cstv.app.domain.model.PlaybackPosition
import com.cstv.app.domain.model.FavoriteItem
import com.cstv.app.domain.model.LiveCategory
import com.cstv.app.domain.model.LiveStream
import com.cstv.app.domain.model.VodStream
import com.cstv.app.domain.model.SeriesStream
import com.cstv.app.domain.model.SeriesCategory
import com.cstv.app.domain.model.VodCategory
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
import com.cstv.app.domain.usecase.GetSeriesCategoriesUseCase
import com.cstv.app.domain.usecase.GetVodCategoriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.collectLatest
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
import com.cstv.app.presentation.components.TrailerPreviewUiState

private const val EPG_POLL_INTERVAL_MILLIS = 60_000L
private const val HOME_ROW_LIMIT = 20

/**
 * Attente maximale du premier résultat de tendances avant d'afficher l'accueil.
 * Au-delà, l'accueil s'affiche sans Hero Card plutôt que de paraître figé.
 */
private const val FIRST_TRENDING_MAX_WAIT_MS = 2_000L

data class HomeState(
    val isLoading: Boolean = false,
    val resumeWatchingList: List<PlaybackPosition> = emptyList(),
    val favoritesList: List<FavoriteItem> = emptyList(),
    val trendingList: List<com.cstv.app.domain.model.TrendingCatalogItem> = emptyList(),
    /**
     * Vrai tant que le tout premier résultat de tendances n'est pas connu et
     * que la borne d'attente n'est pas écoulée : l'accueil patiente au lieu de
     * s'afficher puis d'insérer la Hero Card après coup.
     */
    val awaitingTrending: Boolean = false,
    
    val firstLiveCategory: LiveCategory? = null,
    val firstLiveStreams: List<LiveStream> = emptyList(),
    
    /** Médias de la première catégorie VOD visible du profil (titre générique « Films »). */
    val firstVodCategory: VodCategory? = null,
    val firstVodStreams: List<VodStream> = emptyList(),
    /** Médias de la première catégorie Séries visible du profil (titre générique « Séries »). */
    val firstSeriesCategory: SeriesCategory? = null,
    val firstSeriesStreams: List<SeriesStream> = emptyList(),
    
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
    private val getVodCategoriesUseCase: GetVodCategoriesUseCase,
    private val getSeriesCategoriesUseCase: GetSeriesCategoriesUseCase,
    private val categoryPreferenceRepository: CategoryPreferenceRepository,
    private val getTrendingInCatalogUseCase: com.cstv.app.domain.usecase.GetTrendingInCatalogUseCase,
    private val getRecommendationsUseCase: GetRecommendationsUseCase,
    private val getPopularTop10InCatalogUseCase: GetPopularTop10InCatalogUseCase,
    private val removeFromContinueWatchingUseCase: com.cstv.app.domain.usecase.RemoveFromContinueWatchingUseCase,
    private val getTrailerPreviewUseCase: GetTrailerPreviewUseCase,
    private val invalidateTrailerPreviewUseCase: com.cstv.app.domain.usecase.InvalidateTrailerPreviewUseCase,
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
    private var firstTrendingResolved = false

    /**
     * T8-R2 : une rangée Popular figée (cache affiché ou premier résultat
     * réseau appliqué) ne doit plus être relue par un `loadHomeData()`
     * ultérieur dans la même session (ex : changement de préférences de
     * catégories) — seule une absence encore non résolue continue d'être
     * retentée. Réinitialisées uniquement sur un reset complet (changement de
     * profil), pas sur un simple rechargement.
     */
    private var popularVodResolvedForSession = false
    private var popularSeriesResolvedForSession = false

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
            viewModelScope.launch { invalidateTrailerPreviewUseCase(media) }
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
        //
        // Les recommandations sont invalidées **avant** le rechargement : leur
        // résultat est mis en cache 24 h par profil (`GetRecommendationsUseCase`)
        // et le filtrage des catégories masquées a lieu au **calcul**, pas à la
        // lecture. Sans cette invalidation, masquer une catégorie ne changeait
        // rien à la rangée « recommandé pour vous », qui continuait d'en proposer
        // les médias jusqu'à l'expiration du cache ou la mort du processus —
        // seule la reprise de lecture invalidait ce cache jusqu'ici.
        viewModelScope.launch {
            categoryPreferenceRepository.changes.collect {
                if (hasActiveProfile) {
                    getRecommendationsUseCase.invalidateCache()
                    loadHomeData()
                }
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
                val (vodCatalogIsEmpty, seriesCatalogIsEmpty) = try {
                    !vodRepository.hasCachedVodStreams() to !seriesRepository.hasCachedSeriesStreams()
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    // En cas d'erreur, la catégorie inconnue reste masquée : le
                    // repli protège les préférences de visibilité du profil.
                    false to false
                }

                _state.update {
                    it.copy(
                        resumeWatchingList = groupResumeWatching(
                            allPositions = allPositions,
                            hiddenVodCategories = hiddenVod,
                            hiddenSeriesCategories = hiddenSeries,
                            vodCatalogIsEmpty = vodCatalogIsEmpty,
                            seriesCatalogIsEmpty = seriesCatalogIsEmpty
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
        //
        // Le sondage n'est actif QUE tant qu'un collecteur observe `state`
        // (écran Accueil affiché). Deux raisons :
        //  - prod : aucun rafraîchissement EPG inutile quand l'Accueil n'est
        //    pas à l'écran ;
        //  - tests : un `while (true) { delay(...) }` inconditionnel garde en
        //    permanence une tâche planifiée sur le scheduler virtuel. Le
        //    nettoyage de `runTest` (`advanceUntilIdleOr`) et tout appel à
        //    `advanceUntilIdle()` bouclent alors indéfiniment, ce qui fige le
        //    build sans jamais déclencher le timeout de `runTest` (la boucle
        //    de drainage n'est pas suspendable, donc non interruptible).
        //    Adossé à `subscriptionCount`, le ticker ne planifie plus rien
        //    quand personne n'observe : le scheduler redevient inactif.
        viewModelScope.launch {
            _state.subscriptionCount
                .map { it > 0 }
                .distinctUntilChanged()
                .collectLatest { isObserved ->
                    if (!isObserved) return@collectLatest
                    while (true) {
                        delay(EPG_POLL_INTERVAL_MILLIS)
                        refreshVisibleEpg()
                    }
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
        vodCatalogIsEmpty: Boolean,
        seriesCatalogIsEmpty: Boolean
    ): List<PlaybackPosition> {
        val resumeWatchingRaw = allPositions.filter { pos ->
            pos.positionMs > 0 && pos.positionMs < (pos.durationMs - 15000L)
        }
        val filtered = resumeWatchingRaw.filter { pos ->
            val isSeries = pos.seriesId != null
            val hiddenCategories = if (isSeries) hiddenSeriesCategories else hiddenVodCategories
            val catalogIsEmpty = if (isSeries) seriesCatalogIsEmpty else vodCatalogIsEmpty
            when {
                pos.categoryId != null -> pos.categoryId !in hiddenCategories
                catalogIsEmpty -> true
                else -> false
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
            popularVodResolvedForSession = false
            popularSeriesResolvedForSession = false
            _state.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    firstLiveCategory = null,
                    firstLiveStreams = emptyList(),
                    firstVodCategory = null,
                    firstVodStreams = emptyList(),
                    firstSeriesCategory = null,
                    firstSeriesStreams = emptyList(),
                    recommendedMovies = emptyList(),
                    recommendedSeries = emptyList(),
                    trendingList = emptyList(),
                    epgPrograms = emptyMap(),
                    popularTopVodStreams = null,
                    popularTopSeriesStreams = null,
                    trailerPreview = TrailerPreviewUiState.Poster
                )
            }
            cancelTrendingPreview()
        }

        // F9 / T8 : Popular est isolé du chargement local afin que le fallback
        // soit immédiatement disponible et que TMDB ne puisse jamais bloquer
        // Home. Films et Séries sont traités indépendamment (règle 7) : le
        // cache de l'un n'attend ni ne bloque le premier chargement de l'autre.
        // T8-R2 : une rangée déjà figée pour la session n'est pas relue ici,
        // sinon un rechargement (ex : préférences de catégories) republierait
        // une actualisation silencieuse déjà persistée et casserait la
        // stabilité de session promise par T8.
        popularJob = viewModelScope.launch {
            if (!popularVodResolvedForSession) {
                launch {
                    loadPopularRow(
                        cached = getPopularTop10InCatalogUseCase::cachedMovies,
                        isCacheExpired = getPopularTop10InCatalogUseCase::isMoviesCacheExpired,
                        loadFresh = getPopularTop10InCatalogUseCase::loadFreshMovies,
                        refreshSilently = getPopularTop10InCatalogUseCase::refreshMoviesSilently
                    ) { movies ->
                        popularVodResolvedForSession = true
                        _state.update { it.copy(popularTopVodStreams = movies) }
                    }
                }
            }
            if (!popularSeriesResolvedForSession) {
                launch {
                    loadPopularRow(
                        cached = getPopularTop10InCatalogUseCase::cachedSeries,
                        isCacheExpired = getPopularTop10InCatalogUseCase::isSeriesCacheExpired,
                        loadFresh = getPopularTop10InCatalogUseCase::loadFreshSeries,
                        refreshSilently = getPopularTop10InCatalogUseCase::refreshSeriesSilently
                    ) { series ->
                        popularSeriesResolvedForSession = true
                        _state.update { it.copy(popularTopSeriesStreams = series) }
                    }
                }
            }
        }

        // Tendances TMDB (F1) : appel réseau externe potentiellement lent/instable
        // (DNS, timeout). Découplé du chargement principal (Live/VOD/Séries, tout
        // local) pour ne JAMAIS bloquer le reste de la Home ni le spinner
        // isLoading — sinon un TMDB lent/hors-ligne fait paraître la Home comme
        // chargeant indéfiniment. Timeout client dur en garde-fou supplémentaire
        // (au cas où un appel suspendu ignorerait les timeouts OkHttp).
        //
        // Deux temps : le cache persistant est publié immédiatement (aucun
        // réseau) pour que la Hero Card soit là dès le premier rendu de
        // l'accueil, puis le rafraîchissement remplace la liste seulement s'il
        // rapporte autre chose. Sans cela, la Hero s'insérait après coup et
        // décalait toute la mise en page à chaque lancement.
        val awaitFirstTrending = !firstTrendingResolved
        if (awaitFirstTrending) {
            _state.update { it.copy(awaitingTrending = true) }
        }
        trendingJob = viewModelScope.launch {
            val isExpired = try {
                getTrendingInCatalogUseCase.isCacheExpired()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                true
            }
            val cached = try {
                getTrendingInCatalogUseCase.cached()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                emptyList()
            }
            if (cached.isNotEmpty()) {
                _state.update { it.copy(trendingList = cached, awaitingTrending = false) }
                firstTrendingResolved = true
            }
            val refreshed = try {
                kotlinx.coroutines.withTimeoutOrNull(15_000L) {
                    getTrendingInCatalogUseCase()
                } ?: emptyList()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                emptyList()
            }
            firstTrendingResolved = true
            _state.update { current ->
                // Une liste vide (TMDB injoignable, quota, aucun appariement) ne
                // doit jamais effacer des tendances déjà affichées.
                val keepCurrent = refreshed.isEmpty() || refreshed == current.trendingList
                if (keepCurrent) {
                    current.copy(awaitingTrending = false)
                } else {
                    val newList = if (isExpired && current.trendingList.isNotEmpty()) {
                        val existingIds = current.trendingList
                            .map { it.trendingTitle.tmdbId to it.trendingTitle.isMovie }
                            .toSet()
                        val uniqueRefreshed = refreshed.filter {
                            (it.trendingTitle.tmdbId to it.trendingTitle.isMovie) !in existingIds
                        }
                        current.trendingList + uniqueRefreshed
                    } else {
                        refreshed
                    }
                    current.copy(
                        trendingList = newList,
                        awaitingTrending = false
                    )
                }
            }
        }
        if (awaitFirstTrending) {
            // Borne dure : au tout premier affichage, sans cache exploitable,
            // l'accueil ne reste pas suspendu au réseau TMDB.
            viewModelScope.launch {
                delay(FIRST_TRENDING_MAX_WAIT_MS)
                _state.update { if (it.awaitingTrending) it.copy(awaitingTrending = false) else it }
            }
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

                // 4. Films : première catégorie visible, déjà ordonnée par profil.
                val vodCategories = try {
                    getVodCategoriesUseCase.once()
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    emptyList()
                }
                val firstVodCat = vodCategories.firstOrNull()
                val firstVodStreams = if (firstVodCat != null) {
                    try {
                        vodRepository.getCachedVodStreams(firstVodCat.categoryId).take(HOME_ROW_LIMIT)
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        emptyList()
                    }
                } else emptyList()

                // 5. Séries : première catégorie visible, déjà ordonnée par profil.
                val seriesCategories = try {
                    getSeriesCategoriesUseCase.once()
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    emptyList()
                }
                val firstSeriesCat = seriesCategories.firstOrNull()
                val firstSeriesStreams = if (firstSeriesCat != null) {
                    try {
                        seriesRepository.getCachedSeriesStreams(firstSeriesCat.categoryId).take(HOME_ROW_LIMIT)
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        emptyList()
                    }
                } else emptyList()

                _state.update {
                    it.copy(
                        isLoading = false,
                        firstLiveCategory = firstLiveCat,
                        firstLiveStreams = firstLiveStreams,
                        firstVodCategory = firstVodCat,
                        firstVodStreams = firstVodStreams,
                        firstSeriesCategory = firstSeriesCat,
                        firstSeriesStreams = firstSeriesStreams
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

    /**
     * T8 : si un cache existe (quel que soit son âge), il est affiché
     * immédiatement et figé pour toute la session — l'actualisation réseau
     * qui suit persiste silencieusement sans jamais republier dans
     * [applyToState] (règles 1, 4, 6). Sans cache, le premier résultat réseau
     * est affiché dès réception pour éviter une ligne vide (règle 5).
     *
     * T8-R1 : l'actualisation silencieuse n'est lancée que si [isCacheExpired]
     * le confirme — un cache encore frais ne doit générer ni appel TMDB ni
     * écriture persistante.
     *
     * T8-R3 : `refreshSilently` est lancé via le receveur [CoroutineScope] de
     * cette fonction (enfant du `launch` appelant, lui-même enfant de
     * `popularJob`), pas via `viewModelScope` — un `popularJob?.cancel()`
     * l'annule donc avec le reste au lieu de le laisser tourner en concurrence
     * d'un rechargement suivant.
     */
    private suspend fun <T> CoroutineScope.loadPopularRow(
        cached: suspend () -> List<T>?,
        isCacheExpired: suspend () -> Boolean,
        loadFresh: suspend () -> List<T>?,
        refreshSilently: suspend () -> Unit,
        applyToState: (List<T>) -> Unit
    ) {
        val cachedResult = try {
            kotlinx.coroutines.withTimeoutOrNull(15_000L) { cached() }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
        if (cachedResult != null) {
            applyToState(cachedResult)
            val expired = try {
                isCacheExpired()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                false
            }
            if (expired) {
                launch { runCatching { refreshSilently() } }
            }
            return
        }
        val fresh = try {
            kotlinx.coroutines.withTimeoutOrNull(15_000L) { loadFresh() }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
        if (fresh != null) applyToState(fresh)
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
