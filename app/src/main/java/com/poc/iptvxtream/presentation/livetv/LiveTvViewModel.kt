package com.poc.iptvxtream.presentation.livetv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poc.iptvxtream.data.local.storage.CredentialsManager
import com.poc.iptvxtream.domain.model.Credentials
import com.poc.iptvxtream.domain.model.LiveCategory
import com.poc.iptvxtream.domain.model.LiveStream
import com.poc.iptvxtream.domain.usecase.GetLiveCategoriesUseCase
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
    private val credentialsManager: CredentialsManager
) : ViewModel() {

    private val _state = MutableStateFlow(LiveTvState())
    val state: StateFlow<LiveTvState> = _state.asStateFlow()

    init {
        loadCategories()
        loadRecentlyWatched()
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
                _state.update { 
                    it.copy(
                        categories = categories, 
                        selectedCategory = categories.firstOrNull(),
                        isLoadingCategories = false
                    ) 
                }
                categories.firstOrNull()?.let { 
                    loadStreams(it.categoryId, forceRefresh)
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoadingCategories = false, error = e.message ?: "Impossible de charger les catégories.") }
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
