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
import com.poc.iptvxtream.domain.repository.FavoritesRepository
import com.poc.iptvxtream.domain.repository.LiveTvRepository
import com.poc.iptvxtream.domain.repository.SeriesRepository
import com.poc.iptvxtream.domain.repository.VodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val vodRepository: VodRepository,
    private val liveTvRepository: LiveTvRepository,
    private val seriesRepository: SeriesRepository,
    private val favoritesRepository: FavoritesRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        loadHomeData()
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
