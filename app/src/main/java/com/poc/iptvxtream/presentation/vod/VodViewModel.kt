package com.poc.iptvxtream.presentation.vod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poc.iptvxtream.data.local.storage.CredentialsManager
import com.poc.iptvxtream.domain.model.Credentials
import com.poc.iptvxtream.data.local.storage.SettingsManager
import com.poc.iptvxtream.domain.model.VodCategory
import com.poc.iptvxtream.domain.model.VodDetails
import com.poc.iptvxtream.domain.model.VodStream
import com.poc.iptvxtream.domain.usecase.GetVodCategoriesUseCase
import com.poc.iptvxtream.domain.usecase.GetVodDetailsUseCase
import com.poc.iptvxtream.domain.usecase.GetVodStreamsUseCase
import com.poc.iptvxtream.domain.usecase.SavePlaybackPositionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VodViewModel @Inject constructor(
    private val getVodCategoriesUseCase: GetVodCategoriesUseCase,
    private val getVodStreamsUseCase: GetVodStreamsUseCase,
    private val getVodDetailsUseCase: GetVodDetailsUseCase,
    private val savePlaybackPositionUseCase: SavePlaybackPositionUseCase,
    private val credentialsManager: CredentialsManager,
    private val settingsManager: SettingsManager
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

    fun getSubtitleStyle() = settingsManager.getSubtitleStyle()

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
            } catch (e: Exception) {
                _state.update { it.copy(isLoadingStreams = false, error = e.message ?: "Impossible de charger les films.") }
            }
        }
    }

    fun selectStream(stream: VodStream?) {
        _state.update { it.copy(selectedStream = stream, selectedVodDetails = null) }
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
            } catch (e: Exception) {
                _state.update { it.copy(isLoadingDetails = false, error = e.message ?: "Impossible de charger les détails du film.") }
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
