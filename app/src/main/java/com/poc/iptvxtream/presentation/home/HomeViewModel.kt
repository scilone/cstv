package com.poc.iptvxtream.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poc.iptvxtream.domain.model.PlaybackPosition
import com.poc.iptvxtream.domain.model.FavoriteItem
import com.poc.iptvxtream.domain.model.LiveCategory
import com.poc.iptvxtream.domain.model.LiveStream
import com.poc.iptvxtream.domain.model.VodStream
import com.poc.iptvxtream.domain.model.SeriesStream
import com.poc.iptvxtream.domain.model.CategoryType
import com.poc.iptvxtream.domain.repository.CategoryPreferenceRepository
import com.poc.iptvxtream.domain.repository.FavoritesRepository
import com.poc.iptvxtream.domain.repository.LiveTvRepository
import com.poc.iptvxtream.domain.repository.SeriesRepository
import com.poc.iptvxtream.domain.repository.VodRepository
import com.poc.iptvxtream.domain.usecase.GetLiveCategoriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.poc.iptvxtream.domain.model.LiveEpgProgram
import com.poc.iptvxtream.domain.usecase.GetLiveEpgUseCase

private const val EPG_POLL_INTERVAL_MILLIS = 60_000L

data class HomeState(
    val isLoading: Boolean = false,
    val resumeWatchingList: List<PlaybackPosition> = emptyList(),
    val favoritesList: List<FavoriteItem> = emptyList(),
    
    val firstLiveCategory: LiveCategory? = null,
    val firstLiveStreams: List<LiveStream> = emptyList(),
    
    val firstVodStreams: List<VodStream> = emptyList(),
    val firstSeriesStreams: List<SeriesStream> = emptyList(),
    
    val error: String? = null,
    val epgPrograms: Map<Int, LiveEpgProgram> = emptyMap()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val vodRepository: VodRepository,
    private val liveTvRepository: LiveTvRepository,
    private val seriesRepository: SeriesRepository,
    private val favoritesRepository: FavoritesRepository,
    private val getLiveEpgUseCase: GetLiveEpgUseCase,
    private val getLiveCategoriesUseCase: GetLiveCategoriesUseCase,
    private val categoryPreferenceRepository: CategoryPreferenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private val scrollPositions = mutableMapOf<String, Pair<Int, Int>>()

    fun saveScrollPosition(key: String, index: Int, offset: Int) {
        scrollPositions[key] = Pair(index, offset)
    }

    fun getScrollPosition(key: String): Pair<Int, Int> {
        return scrollPositions[key] ?: Pair(0, 0)
    }

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
        loadHomeData()
        // Phase 58 : recharge la Home quand les préférences de catégories
        // (masquage/ordre) changent — le ViewModel est partagé au niveau app.
        viewModelScope.launch {
            categoryPreferenceRepository.changes.collect { loadHomeData() }
        }
        // Phase 41 : "Continuer à regarder" et "Favoris" restent à jour en
        // continu (ajout/suppression d'un favori ailleurs dans l'app, reprise
        // de lecture) sans reload manuel ni ré-écoute du changement de profil
        // (déjà géré par le flatMapLatest des repositories sur activeProfileId).
        viewModelScope.launch {
            vodRepository.observeAllPlaybackPositions().collect { allPositions ->
                _state.update { it.copy(resumeWatchingList = groupResumeWatching(allPositions)) }
            }
        }
        viewModelScope.launch {
            favoritesRepository.observeFavorites().collect { favorites ->
                _state.update { it.copy(favoritesList = favorites) }
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
    private fun groupResumeWatching(allPositions: List<PlaybackPosition>): List<PlaybackPosition> {
        val resumeWatchingRaw = allPositions.filter { pos ->
            pos.positionMs > 0 && pos.positionMs < (pos.durationMs - 15000L)
        }
        val seenSeriesIds = mutableSetOf<Int>()
        return resumeWatchingRaw.filter { pos ->
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

    fun loadHomeData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                // "Continuer à regarder" et "Favoris" sont alimentés en continu par les
                // Flow collectés dans init() (voir groupResumeWatching) : plus de fetch
                // ponctuel ici.

                // 3. Fetch TV - First Live Category and its Streams
                // (catégories filtrées/ordonnées selon les préférences du profil, Phase 58)
                val liveCategories = try {
                    getLiveCategoriesUseCase(forceRefresh = false)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    emptyList()
                }
                val firstLiveCat = liveCategories.firstOrNull()
                val firstLiveStreams = if (firstLiveCat != null) {
                    try {
                        liveTvRepository.getLiveStreams(firstLiveCat.categoryId, forceRefresh = false)
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        emptyList()
                    }
                } else emptyList()

                // 4. Fetch Movies - Latest additions (toutes catégories confondues)
                val allVodStreams = try {
                    vodRepository.getVodStreams("all", forceRefresh = false)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    emptyList()
                }
                val hiddenVodCategories = hiddenCategoryIds(CategoryType.VOD)
                val firstVodStreams = allVodStreams
                    .filter { it.categoryId !in hiddenVodCategories }
                    .sortedByDescending { it.added?.toLongOrNull() ?: 0L }
                    .take(20)

                // 5. Fetch Series - Latest additions (toutes catégories confondues)
                val allSeriesStreams = try {
                    seriesRepository.getSeriesStreams("all", forceRefresh = false)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    emptyList()
                }
                val hiddenSeriesCategories = hiddenCategoryIds(CategoryType.SERIES)
                val firstSeriesStreams = allSeriesStreams
                    .filter { it.categoryId !in hiddenSeriesCategories }
                    .sortedByDescending { it.added?.toLongOrNull() ?: 0L }
                    .take(20)

                _state.update {
                    it.copy(
                        isLoading = false,
                        firstLiveCategory = firstLiveCat,
                        firstLiveStreams = firstLiveStreams,
                        firstVodStreams = firstVodStreams,
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
}
