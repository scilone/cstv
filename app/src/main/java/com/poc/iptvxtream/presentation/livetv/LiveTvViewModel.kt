package com.poc.iptvxtream.presentation.livetv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poc.iptvxtream.data.local.storage.CredentialsManager
import com.poc.iptvxtream.domain.model.Credentials
import com.poc.iptvxtream.domain.model.LiveCategory
import com.poc.iptvxtream.domain.model.LiveStream
import com.poc.iptvxtream.domain.usecase.GetLiveCategoriesUseCase
import com.poc.iptvxtream.domain.usecase.GetLiveCategoryCountsUseCase
import com.poc.iptvxtream.domain.usecase.GetLiveEpgUseCase
import com.poc.iptvxtream.domain.usecase.GetLiveEpgNowNextUseCase
import com.poc.iptvxtream.domain.model.LiveEpgNowNext
import com.poc.iptvxtream.domain.usecase.GetLiveStreamsUseCase
import com.poc.iptvxtream.domain.usecase.GetRecentlyWatchedUseCase
import com.poc.iptvxtream.domain.usecase.SaveRecentlyWatchedUseCase
import com.poc.iptvxtream.data.local.storage.SettingsManager
import com.poc.iptvxtream.data.local.storage.ResizeMode
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
    private val getLiveCategoryCountsUseCase: GetLiveCategoryCountsUseCase,
    private val getLiveStreamsUseCase: GetLiveStreamsUseCase,
    private val getRecentlyWatchedUseCase: GetRecentlyWatchedUseCase,
    private val saveRecentlyWatchedUseCase: SaveRecentlyWatchedUseCase,
    private val getLiveEpgUseCase: GetLiveEpgUseCase,
    private val getLiveEpgNowNextUseCase: GetLiveEpgNowNextUseCase,
    private val credentialsManager: CredentialsManager,
    private val categoryPreferenceRepository: com.poc.iptvxtream.domain.repository.CategoryPreferenceRepository,
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val _state = MutableStateFlow(LiveTvState())
    val state: StateFlow<LiveTvState> = _state.asStateFlow()

    fun getResizeMode(): ResizeMode = settingsManager.getResizeMode()
    fun setResizeMode(mode: ResizeMode) {
        settingsManager.setResizeMode(mode)
    }

    // EPG « en cours + suivant » pour le player Live TV (Phase 60, informatif).
    // Rechargé à chaque changement de chaîne dans le player ; null tant que non
    // chargé ou si l'EPG est indisponible.
    private val _playerEpg = MutableStateFlow<LiveEpgNowNext?>(null)
    val playerEpg: StateFlow<LiveEpgNowNext?> = _playerEpg.asStateFlow()

    fun loadPlayerEpg(streamId: Int) {
        _playerEpg.value = null
        viewModelScope.launch {
            try {
                _playerEpg.value = getLiveEpgNowNextUseCase(streamId)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _playerEpg.value = null
            }
        }
    }

    private val scrollPositions = mutableMapOf<String, Pair<Int, Int>>()

    fun saveScrollPosition(key: String, index: Int, offset: Int) {
        scrollPositions[key] = Pair(index, offset)
    }

    fun getScrollPosition(key: String): Pair<Int, Int> {
        return scrollPositions[key] ?: Pair(0, 0)
    }

    init {
        loadCategories()
        loadRecentlyWatched()
        // Recharge les catégories quand les préférences (masquage/ordre, Phase 58)
        // changent : ce ViewModel survit en backstack pendant le passage par les
        // Paramètres, l'init seul ne suffit pas.
        viewModelScope.launch {
            categoryPreferenceRepository.changes.collect { loadCategories() }
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

    fun loadRecentlyWatched() {
        viewModelScope.launch {
            try {
                val list = getRecentlyWatchedUseCase()
                _state.update { it.copy(recentlyWatched = list) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
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
                if (e is kotlinx.coroutines.CancellationException) throw e
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
                if (e is kotlinx.coroutines.CancellationException) throw e
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
                refreshCategoryCounts()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(isLoadingStreams = false, error = e.message ?: "Impossible de charger les chaînes.") }
            }
        }
    }

    // Compteurs de la bottom sheet, rafraîchis après chaque sync de streams
    // (le cache local vient potentiellement de changer). Échec silencieux :
    // les compteurs sont un enrichissement, jamais bloquants.
    private fun refreshCategoryCounts() {
        viewModelScope.launch {
            try {
                val counts = getLiveCategoryCountsUseCase()
                _state.update { it.copy(categoryCounts = counts) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
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
