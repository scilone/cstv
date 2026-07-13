package com.poc.iptvxtream.presentation.livetv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poc.iptvxtream.data.local.storage.CredentialsManager
import com.poc.iptvxtream.domain.model.Credentials
import com.poc.iptvxtream.domain.model.LiveCategory
import com.poc.iptvxtream.domain.model.LiveStream
import com.poc.iptvxtream.domain.usecase.GetLiveCategoriesUseCase
import com.poc.iptvxtream.domain.usecase.GetLiveEpgUseCase
import com.poc.iptvxtream.domain.usecase.GetLiveStreamsUseCase
import com.poc.iptvxtream.domain.usecase.GetRecentlyWatchedUseCase
import com.poc.iptvxtream.domain.usecase.SaveRecentlyWatchedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val getLiveCategoriesUseCase: GetLiveCategoriesUseCase,
    private val getLiveStreamsUseCase: GetLiveStreamsUseCase,
    private val getRecentlyWatchedUseCase: GetRecentlyWatchedUseCase,
    private val saveRecentlyWatchedUseCase: SaveRecentlyWatchedUseCase,
    private val getLiveEpgUseCase: GetLiveEpgUseCase,
    private val credentialsManager: CredentialsManager
) : ViewModel() {

    private val _state = MutableStateFlow(LiveTvState())
    val state: StateFlow<LiveTvState> = _state.asStateFlow()

    init {
        loadCategories()
        loadRecentlyWatched()
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

    fun loadRecentlyWatched() {
        viewModelScope.launch {
            try {
                val list = getRecentlyWatchedUseCase()
                _state.update { it.copy(recentlyWatched = list) }
            } catch (e: Exception) {
                // Ignore gracefully for recently watched
            }
        }
    }

    fun saveRecentlyWatched(stream: LiveStream) {
        viewModelScope.launch {
            try {
                saveRecentlyWatchedUseCase(stream)
                loadRecentlyWatched() // refresh state after saving
            } catch (e: Exception) {
                // Ignore gracefully
            }
        }
    }

    fun loadCategories(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingCategories = true, error = null) }
            try {
                val categories = getLiveCategoriesUseCase(forceRefresh)
                val finalCategories = listOf(LiveCategory("all", "Tout", 0)) + categories
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
                _state.update { it.copy(isLoadingCategories = false, error = e.message ?: "Une erreur est survenue.") }
            }
        }
    }

    fun selectCategory(category: LiveCategory) {
        _state.update { it.copy(selectedCategory = category, streams = emptyList()) }
        loadStreams(category.categoryId)
    }

    fun loadStreams(categoryId: String, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingStreams = true, error = null) }
            try {
                val streams = getLiveStreamsUseCase(categoryId, forceRefresh)
                _state.update { it.copy(streams = streams, isLoadingStreams = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoadingStreams = false, error = e.message ?: "Impossible de charger les chaînes.") }
            }
        }
    }

    fun selectStream(stream: LiveStream?) {
        _state.update { it.copy(selectedStream = stream) }
    }

    fun getCredentials(): Credentials? {
        return credentialsManager.getCredentials()
    }
}
