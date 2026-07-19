package com.cstv.app.presentation.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cstv.app.data.local.storage.CredentialsManager
import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.model.SeriesCategory
import com.cstv.app.domain.model.SeriesDetails
import com.cstv.app.domain.model.SeriesEpisode
import com.cstv.app.domain.model.SeriesStream
import com.cstv.app.domain.usecase.GetSeriesCategoriesUseCase
import com.cstv.app.domain.usecase.GetSeriesCategoryCountsUseCase
import com.cstv.app.domain.usecase.GetSeriesDetailsUseCase
import com.cstv.app.domain.usecase.GetRelatedSeriesUseCase
import com.cstv.app.domain.usecase.GetSeriesStreamsUseCase
import com.cstv.app.domain.usecase.SavePlaybackPositionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val seriesRepository: com.cstv.app.domain.repository.SeriesRepository
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
        // Recharge les catégories au changement de préférences (masquage/ordre,
        // Phase 58) : le ViewModel survit en backstack pendant les Paramètres.
        viewModelScope.launch {
            categoryPreferenceRepository.changes.collect { loadCategories() }
        }
        // Observe et filtre les positions de lecture en temps réel (F5)
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                vodRepository.observeAllPlaybackPositions(),
                categoryPreferenceRepository.changes
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

                val seriesMap = try {
                    seriesRepository.getSeriesStreams("all", false).associate { it.seriesId to it.categoryId }
                } catch (e: Exception) {
                    emptyMap()
                }

                val filtered = seriesPositions.filter { pos ->
                    val catId = pos.seriesId?.let { seriesMap[it] }
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
                refreshCategoryCounts()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(isLoadingStreams = false, error = e.message ?: "Impossible de charger les séries.") }
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

    fun selectStream(stream: SeriesStream?) {
        _state.update { it.copy(selectedStream = stream, selectedSeriesDetails = null, relatedSeries = emptyList()) }
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
                loadRelatedSeries(details.seriesId, details.genre)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(isLoadingDetails = false, error = e.message ?: "Impossible de charger les détails de la série.") }
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
