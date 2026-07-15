package com.poc.iptvxtream.presentation.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poc.iptvxtream.data.local.storage.CredentialsManager
import com.poc.iptvxtream.domain.model.Credentials
import com.poc.iptvxtream.domain.model.SeriesCategory
import com.poc.iptvxtream.domain.model.SeriesDetails
import com.poc.iptvxtream.domain.model.SeriesEpisode
import com.poc.iptvxtream.domain.model.SeriesStream
import com.poc.iptvxtream.domain.usecase.GetSeriesCategoriesUseCase
import com.poc.iptvxtream.domain.usecase.GetSeriesDetailsUseCase
import com.poc.iptvxtream.domain.usecase.GetSeriesStreamsUseCase
import com.poc.iptvxtream.domain.usecase.SavePlaybackPositionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.poc.iptvxtream.data.local.storage.SettingsManager

@HiltViewModel
class SeriesViewModel @Inject constructor(
    private val getSeriesCategoriesUseCase: GetSeriesCategoriesUseCase,
    private val getSeriesStreamsUseCase: GetSeriesStreamsUseCase,
    private val getSeriesDetailsUseCase: GetSeriesDetailsUseCase,
    private val savePlaybackPositionUseCase: SavePlaybackPositionUseCase,
    private val credentialsManager: CredentialsManager,
    private val settingsManager: SettingsManager,
    private val trackPreferenceRepository: com.poc.iptvxtream.domain.repository.TrackPreferenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SeriesState())
    val state: StateFlow<SeriesState> = _state.asStateFlow()

    private val scrollPositions = mutableMapOf<String, Pair<Int, Int>>()

    fun saveScrollPosition(key: String, index: Int, offset: Int) {
        scrollPositions[key] = Pair(index, offset)
    }

    fun getScrollPosition(key: String): Pair<Int, Int> {
        return scrollPositions[key] ?: Pair(0, 0)
    }

    init {
        loadCategories()
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
        trackPreferenceRepository.getPreference(com.poc.iptvxtream.domain.model.MediaType.SERIES, seriesId)

    fun saveSeriesAudio(seriesId: Int, lang: String?) {
        settingsManager.setPreferredAudio(lang)
        viewModelScope.launch {
            trackPreferenceRepository.saveAudioLang(com.poc.iptvxtream.domain.model.MediaType.SERIES, seriesId, lang)
        }
    }

    fun saveSeriesSubtitle(seriesId: Int, lang: String?) {
        settingsManager.setPreferredSubtitle(lang)
        viewModelScope.launch {
            trackPreferenceRepository.saveSubtitleLang(com.poc.iptvxtream.domain.model.MediaType.SERIES, seriesId, lang)
        }
    }

    fun getSubtitleStyle() = settingsManager.getSubtitleStyle()

    fun loadCategories(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingCategories = true, error = null) }
            try {
                val categories = getSeriesCategoriesUseCase(forceRefresh)
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
                newSelected?.let {
                    loadStreams(it.categoryId, forceRefresh)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(isLoadingCategories = false, error = e.message ?: "Impossible de charger les catégories séries.") }
            }
        }
    }

    fun selectCategory(category: SeriesCategory) {
        _state.update { it.copy(selectedCategory = category, streams = emptyList()) }
        loadStreams(category.categoryId)
    }

    fun loadStreams(categoryId: String, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingStreams = true, error = null) }
            try {
                val streams = getSeriesStreamsUseCase(categoryId, forceRefresh)
                _state.update { it.copy(streams = streams, isLoadingStreams = false) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(isLoadingStreams = false, error = e.message ?: "Impossible de charger les séries.") }
            }
        }
    }

    fun selectStream(stream: SeriesStream?) {
        _state.update { it.copy(selectedStream = stream, selectedSeriesDetails = null) }
        if (stream != null) {
            loadSeriesDetails(stream.seriesId)
        }
    }

    fun loadSeriesDetails(seriesId: Int) {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingDetails = true, error = null) }
            try {
                val details = getSeriesDetailsUseCase(seriesId)
                _state.update { it.copy(selectedSeriesDetails = details, isLoadingDetails = false) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(isLoadingDetails = false, error = e.message ?: "Impossible de charger les détails de la série.") }
            }
        }
    }

    fun savePosition(
        episode: SeriesEpisode,
        positionMs: Long,
        durationMs: Long,
        seriesName: String,
        seriesCover: String?
    ) {
        viewModelScope.launch {
            // Format the title as: Series Name - SxxEyy Episode Title
            val title = "$seriesName - S${episode.seasonNum}E${episode.episodeNum} ${episode.title}"
            val coverUrl = seriesCover
            val type = "series"
            val containerExtension = episode.containerExtension

            savePlaybackPositionUseCase(
                streamId = episode.id,
                positionMs = positionMs,
                durationMs = durationMs,
                title = title,
                coverUrl = coverUrl,
                type = type,
                containerExtension = containerExtension,
                seriesId = null,
                episodeNum = episode.episodeNum,
                seasonNum = episode.seasonNum,
                plot = episode.plot,
                duration = episode.duration,
                releaseDate = episode.releaseDate
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
            savePlaybackPositionUseCase(episodeId, 0L, 0L)
            _state.value.selectedSeriesDetails?.let { currentDetails ->
                loadSeriesDetails(currentDetails.seriesId)
            }
        }
    }

    fun getCredentials(): Credentials? {
        return credentialsManager.getCredentials()
    }
}
