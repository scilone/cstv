package com.poc.iptvxtream.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poc.iptvxtream.domain.model.PlaybackPosition
import com.poc.iptvxtream.domain.model.FavoriteItem
import com.poc.iptvxtream.domain.model.LiveCategory
import com.poc.iptvxtream.domain.model.LiveStream
import com.poc.iptvxtream.domain.model.VodCategory
import com.poc.iptvxtream.domain.model.VodStream
import com.poc.iptvxtream.domain.model.SeriesCategory
import com.poc.iptvxtream.domain.model.SeriesStream
import com.poc.iptvxtream.data.local.storage.ProfileManager
import com.poc.iptvxtream.domain.repository.FavoritesRepository
import com.poc.iptvxtream.domain.repository.LiveTvRepository
import com.poc.iptvxtream.domain.repository.SeriesRepository
import com.poc.iptvxtream.domain.repository.VodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.poc.iptvxtream.domain.model.LiveEpgProgram
import com.poc.iptvxtream.domain.usecase.GetLiveEpgUseCase

data class HomeState(
    val isLoading: Boolean = false,
    val resumeWatchingList: List<PlaybackPosition> = emptyList(),
    val favoritesList: List<FavoriteItem> = emptyList(),
    
    val firstLiveCategory: LiveCategory? = null,
    val firstLiveStreams: List<LiveStream> = emptyList(),
    
    val firstVodCategory: VodCategory? = null,
    val firstVodStreams: List<VodStream> = emptyList(),
    
    val firstSeriesCategory: SeriesCategory? = null,
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
    private val profileManager: ProfileManager
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

    init {
        loadHomeData()
        // Rafraîchit immédiatement Continuer à regarder / Favoris au changement
        // de profil (Phase 27), sans redémarrage. drop(1) ignore la valeur initiale.
        viewModelScope.launch {
            profileManager.activeProfileId.drop(1).collect { loadHomeData() }
        }
    }

    // Guards against duplicate concurrent fetches and hammering channels without EPG data
    private val epgInFlight = mutableSetOf<Int>()
    private val epgLastAttempt = mutableMapOf<Int, Long>()

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

    fun loadHomeData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                // 1. Fetch Resume Watching list (movies & episodes with position > 0 and position < duration - 15000L)
                val allPositions = vodRepository.getAllPlaybackPositions()
                val resumeWatching = allPositions.filter { pos ->
                    pos.positionMs > 0 && pos.positionMs < (pos.durationMs - 15000L)
                }

                // 2. Fetch Favorites
                val favorites = favoritesRepository.getFavorites()

                // 3. Fetch TV - First Live Category and its Streams
                val liveCategories = try {
                    liveTvRepository.getLiveCategories(forceRefresh = false)
                } catch (e: Exception) {
                    emptyList()
                }
                val firstLiveCat = liveCategories.firstOrNull()
                val firstLiveStreams = if (firstLiveCat != null) {
                    try {
                        liveTvRepository.getLiveStreams(firstLiveCat.categoryId, forceRefresh = false)
                    } catch (e: Exception) {
                        emptyList()
                    }
                } else emptyList()

                // 4. Fetch Movies - First VOD Category and its Streams
                val vodCategories = try {
                    vodRepository.getVodCategories(forceRefresh = false)
                } catch (e: Exception) {
                    emptyList()
                }
                val firstVodCat = vodCategories.firstOrNull()
                val firstVodStreams = if (firstVodCat != null) {
                    try {
                        vodRepository.getVodStreams(firstVodCat.categoryId, forceRefresh = false)
                    } catch (e: Exception) {
                        emptyList()
                    }
                } else emptyList()

                // 5. Fetch Series - First Series Category and its Streams
                val seriesCategories = try {
                    seriesRepository.getSeriesCategories(forceRefresh = false)
                } catch (e: Exception) {
                    emptyList()
                }
                val firstSeriesCat = seriesCategories.firstOrNull()
                val firstSeriesStreams = if (firstSeriesCat != null) {
                    try {
                        seriesRepository.getSeriesStreams(firstSeriesCat.categoryId, forceRefresh = false)
                    } catch (e: Exception) {
                        emptyList()
                    }
                } else emptyList()

                _state.update {
                    it.copy(
                        isLoading = false,
                        resumeWatchingList = resumeWatching,
                        favoritesList = favorites,
                        firstLiveCategory = firstLiveCat,
                        firstLiveStreams = firstLiveStreams,
                        firstVodCategory = firstVodCat,
                        firstVodStreams = firstVodStreams,
                        firstSeriesCategory = firstSeriesCat,
                        firstSeriesStreams = firstSeriesStreams
                    )
                }
            } catch (e: Exception) {
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
