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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val vodRepository: com.cstv.app.domain.repository.VodRepository
) : ViewModel() {

    private val _state = MutableStateFlow(VodState())
    val state: StateFlow<VodState> = _state.asStateFlow()

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
                    vodRepository.getVodStreams("all", false).associate { it.streamId to it.categoryId }
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

    fun loadCategories(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingCategories = true, error = null) }
            try {
                val categories = getVodCategoriesUseCase(forceRefresh)
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
                newSelected?.let {
                    loadStreams(it.categoryId, forceRefresh)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(isLoadingCategories = false, error = e.message ?: "Impossible de charger les catégories VOD.") }
            }
        }
    }

    fun selectCategory(category: VodCategory) {
        _state.update { it.copy(selectedCategory = category, streams = emptyList()) }
        loadStreams(category.categoryId)
    }

    fun loadStreams(categoryId: String, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingStreams = true, error = null) }
            try {
                val streams = getVodStreamsUseCase(categoryId, forceRefresh)
                _state.update { it.copy(streams = streams, isLoadingStreams = false) }
                refreshCategoryCounts()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(isLoadingStreams = false, error = e.message ?: "Impossible de charger les films.") }
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

    fun selectStream(stream: VodStream?) {
        _state.update { it.copy(selectedStream = stream, selectedVodDetails = null, relatedStreams = emptyList()) }
        if (stream != null) {
            loadVodDetails(stream.streamId)
        }
    }

    private fun loadVodDetails(streamId: Int) {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingDetails = true, error = null) }
            try {
                val details = getVodDetailsUseCase(streamId)
                _state.update { it.copy(selectedVodDetails = details, isLoadingDetails = false) }
                loadRelatedMovies(details.streamId, details.genre)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(isLoadingDetails = false, error = e.message ?: "Impossible de charger les détails du film.") }
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
