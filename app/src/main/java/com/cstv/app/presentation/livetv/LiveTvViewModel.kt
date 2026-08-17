package com.cstv.app.presentation.livetv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cstv.app.data.local.storage.CredentialsManager
import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.model.LiveCategory
import com.cstv.app.domain.model.LiveStream
import com.cstv.app.domain.model.LiveVariant
import com.cstv.app.domain.usecase.GetLiveCategoriesUseCase
import com.cstv.app.domain.usecase.GetLiveCategoryCountsUseCase
import com.cstv.app.domain.usecase.GetLiveEpgUseCase
import com.cstv.app.domain.usecase.GetLiveEpgNowNextUseCase
import com.cstv.app.domain.model.LiveEpgNowNext
import com.cstv.app.domain.usecase.GetLiveStreamsUseCase
import com.cstv.app.domain.usecase.ObserveRecentlyWatchedUseCase
import com.cstv.app.domain.usecase.RemoveRecentlyWatchedUseCase
import com.cstv.app.domain.usecase.SaveRecentlyWatchedUseCase
import com.cstv.app.data.local.storage.SettingsManager
import com.cstv.app.data.local.storage.ResizeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.cstv.app.presentation.player.core.LiveQualityController
import com.cstv.app.presentation.player.core.LiveStabilityMonitor

@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val getLiveCategoriesUseCase: GetLiveCategoriesUseCase,
    private val getLiveCategoryCountsUseCase: GetLiveCategoryCountsUseCase,
    private val getLiveStreamsUseCase: GetLiveStreamsUseCase,
    private val observeRecentlyWatchedUseCase: ObserveRecentlyWatchedUseCase,
    private val removeRecentlyWatchedUseCase: RemoveRecentlyWatchedUseCase,
    private val saveRecentlyWatchedUseCase: SaveRecentlyWatchedUseCase,
    private val getLiveEpgUseCase: GetLiveEpgUseCase,
    private val getLiveEpgNowNextUseCase: GetLiveEpgNowNextUseCase,
    private val credentialsManager: CredentialsManager,
    private val categoryPreferenceRepository: com.cstv.app.domain.repository.CategoryPreferenceRepository,
    private val settingsManager: SettingsManager,
    private val liveTvRepository: com.cstv.app.domain.repository.LiveTvRepository,
    private val observeCatalogStatusUseCase: com.cstv.app.domain.usecase.ObserveCatalogStatusUseCase,
    private val catalogSyncManager: com.cstv.app.domain.sync.CatalogSyncManager,
    private val canPlayContentUseCase: com.cstv.app.domain.usecase.CanPlayContentUseCase,
    private val requestPlaybackLockUseCase: com.cstv.app.domain.usecase.PlaybackLockRequester? = null,
    private val releasePlaybackLockUseCase: com.cstv.app.domain.usecase.PlaybackLockReleaser? = null,
    private val playbackLockManager: com.cstv.app.data.playback.PlaybackLockManager? = null,
    /** T23 : autoréparation du lecteur — nullable pour ne pas casser les tests existants qui
     * construisent ce ViewModel positionnellement sans ce paramètre final. */
    val playbackRepairRepository: com.cstv.app.domain.repository.PlaybackRepairRepository? = null,
    /** F40: local grouping is a domain contract; test callers may supply the inert default. */
    private val liveVariantRepository: com.cstv.app.domain.repository.LiveVariantRepository = EmptyLiveVariantRepository,
) : ViewModel() {

    private val qualityController = LiveQualityController()
    private val stabilityMonitor = LiveStabilityMonitor { (System.nanoTime() / 1_000_000L) }

    private val _state = MutableStateFlow(LiveTvState())
    val state: StateFlow<LiveTvState> = _state.asStateFlow()
    private val _playbackLockUiState = MutableStateFlow(com.cstv.app.domain.model.PlaybackLockUiState())
    val playbackLockUiState: StateFlow<com.cstv.app.domain.model.PlaybackLockUiState> = _playbackLockUiState.asStateFlow()
    private var playbackLockRequest: kotlinx.coroutines.Job? = null
    private var playbackLockContentId: String? = null

    init {
        playbackLockManager?.let { manager -> viewModelScope.launch {
            manager.state.collect { lock ->
                if (lock is com.cstv.app.domain.model.PlaybackLockState.Revoked) {
                    _playbackLockUiState.value = _playbackLockUiState.value.copy(canStartPlayback = false, conflict = null, takenOverBy = lock.holder)
                }
            }
        } }
    }

    fun beginPlaybackLock(contentId: String? = null) { playbackLockContentId = contentId; requestPlaybackLock(false) }
    fun takeOverPlaybackLock() = requestPlaybackLock(true)
    fun resumePlaybackLock() = requestPlaybackLock(false)
    fun cancelPlaybackLockRequest() { _playbackLockUiState.value = _playbackLockUiState.value.copy(conflict = null) }
    fun pausePlaybackLock() { releasePlaybackLockUseCase?.release(); _playbackLockUiState.value = _playbackLockUiState.value.copy(canStartPlayback = false) }
    fun releasePlaybackLock() = pausePlaybackLock()
    private fun requestPlaybackLock(takeover: Boolean) {
        playbackLockRequest?.cancel()
        playbackLockRequest = viewModelScope.launch {
            when (val result = requestPlaybackLockUseCase?.request(playbackLockContentId, takeover) ?: com.cstv.app.domain.usecase.PlaybackLockRequestResult.Allowed) {
                com.cstv.app.domain.usecase.PlaybackLockRequestResult.Allowed, com.cstv.app.domain.usecase.PlaybackLockRequestResult.NotRequired ->
                    _playbackLockUiState.value = com.cstv.app.domain.model.PlaybackLockUiState(canStartPlayback = true, failOpen = playbackLockManager?.state?.value is com.cstv.app.domain.model.PlaybackLockState.FailOpen)
                is com.cstv.app.domain.usecase.PlaybackLockRequestResult.Held ->
                    _playbackLockUiState.value = com.cstv.app.domain.model.PlaybackLockUiState(conflict = result.holder)
            }
        }
    }

    private var streamsJob: kotlinx.coroutines.Job? = null
    private var observedCategoryId: String? = null

    fun getResizeMode(): ResizeMode = settingsManager.getResizeMode()
    fun setResizeMode(mode: ResizeMode) {
        settingsManager.setResizeMode(mode)
    }

    fun liveQualityModeDefault(): Boolean = settingsManager.getLiveQualityModeDefault()

    suspend fun startLiveQualitySession(stream: LiveStream): LiveQualityPlayerState {
        val variants = liveVariantRepository.variantsFor(stream.streamId)
        val candidates = variants.ifEmpty { listOf(LiveVariant(stream)) }
        stabilityMonitor.reset()
        val automaticCandidate = qualityController.start("", candidates, liveQualityModeDefault())
        val selected = automaticCandidate?.stream ?: stream
        if (automaticCandidate == null) qualityController.retainManualInitial(LiveVariant(selected))
        return LiveQualityPlayerState(
            variants = variants.takeIf { it.size > 1 }.orEmpty(),
            selectedStream = selected,
            generation = qualityController.generation()
        )
    }

    fun liveQualityGeneration(): Long = qualityController.generation()
    fun onLiveQualityReady(token: Long) {
        stabilityMonitor.onReady()
        qualityController.onReady(token, (System.nanoTime() / 1_000_000L))
    }
    fun onLiveQualityBufferingStarted(token: Long): LiveStream? {
        if (!stabilityMonitor.onBufferingStarted()) return null
        return qualityController.onFailure(token, stabilityMonitor.measurement(), (System.nanoTime() / 1_000_000L))?.stream
    }
    fun onLiveQualityBufferingEnded() = stabilityMonitor.onBufferingEnded()
    fun onLiveQualityFailure(token: Long): LiveStream? =
        qualityController.onFailure(token, stabilityMonitor.measurement(), (System.nanoTime() / 1_000_000L))?.stream
    fun selectLiveQuality(variant: LiveVariant): LiveStream = qualityController.selectManually(variant).stream
    fun resetLiveQualityMeasurement() = stabilityMonitor.reset()

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

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pagedStreams: Flow<PagingData<LiveStream>> = state
        .map { it.selectedCategory?.categoryId }
        .distinctUntilChanged()
        .flatMapLatest { categoryId ->
            if (categoryId != null) {
                liveTvRepository.getLiveStreamsPaged(categoryId)
                    .cachedIn(viewModelScope)
            } else {
                flowOf(PagingData.empty())
            }
        }

    init {
        observeCategories()
        observeCatalogStatus()
        observeSyncCompletion()
        triggerSilentSyncIfStale()
        viewModelScope.launch {
            observeRecentlyWatchedUseCase().collect { list ->
                _state.update { it.copy(recentlyWatched = list) }
            }
        }
    }

    /**
     * T7 (D3) : entrer sur l'onglet Live TV déclenche silencieusement une
     * synchronisation si le catalogue est périmé — pas d'erreur, pas de
     * bannière, pas d'action manuelle. `syncIfStale()` est déjà un no-op si le
     * catalogue est frais, hors ligne, ou une synchronisation est en cours.
     */
    private fun triggerSilentSyncIfStale() {
        viewModelScope.launch {
            runCatching { catalogSyncManager.syncIfStale() }
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

    fun saveRecentlyWatched(stream: LiveStream) {
        viewModelScope.launch {
            try {
                saveRecentlyWatchedUseCase(stream)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Ignore gracefully
            }
        }
    }

    fun removeRecentlyWatched(stream: LiveStream) = viewModelScope.launch {
        _state.update { it.copy(isRemovingHistory = true, historyRemovalError = null) }
        try {
            removeRecentlyWatchedUseCase(stream.streamId)
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            _state.update { it.copy(historyRemovalError = "Impossible de retirer cet élément. Réessayez.") }
        } finally {
            _state.update { it.copy(isRemovingHistory = false) }
        }
    }

    fun consumeHistoryRemovalError() { _state.update { it.copy(historyRemovalError = null) } }

    /** Voir `VodViewModel.observeCategories` : lecture Room, jamais réseau. */
    private fun observeCategories() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingCategories = it.categories.isEmpty()) }
            // Voir VodViewModel : l'abonnement aux flux n'a lieu qu'une fois les
            // catégories arrivées, donc tout retard ici décale d'autant la
            // requête de liste — et le voile de chargement avec.
            val subscribedAt = System.nanoTime()
            var firstEmission = true
            getLiveCategoriesUseCase().collect { categories ->
                if (firstEmission) {
                    firstEmission = false
                    com.cstv.app.di.IptvLog.d(
                        "PERF",
                        "Live première émission catégories=${categories.size} " +
                            "en ${(System.nanoTime() - subscribedAt) / 1_000_000}ms"
                    )
                }
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
                newSelected?.let { observeStreams(it.categoryId) }
            }
        }
    }

    private fun observeCatalogStatus() {
        viewModelScope.launch {
            observeCatalogStatusUseCase().collect { status ->
                _state.update { it.copy(catalogStatus = status) }
            }
        }
    }

    /** Voir `VodViewModel.observeSyncCompletion`. */
    private fun observeSyncCompletion() {
        viewModelScope.launch {
            catalogSyncManager.syncState.collect { syncState ->
                if (syncState !is com.cstv.app.domain.sync.SyncState.Success) return@collect
                val categoryId = _state.value.selectedCategory?.categoryId ?: return@collect
                resubscribeStreams(categoryId)
            }
        }
    }

    /** Voir `VodViewModel.selectCategory` : pas de vidage sans changement réel. */
    fun selectCategory(category: LiveCategory) {
        val isSameCategory = _state.value.selectedCategory?.categoryId == category.categoryId
        _state.update {
            it.copy(
                selectedCategory = category,
                streams = if (isSameCategory) it.streams else emptyList()
            )
        }
        observeStreams(category.categoryId)
    }

    /** Voir `VodViewModel.resubscribeStreams`. */
    private fun resubscribeStreams(categoryId: String) {
        observedCategoryId = null
        observeStreams(categoryId)
    }

    private fun observeStreams(categoryId: String) {
        if (observedCategoryId == categoryId && streamsJob?.isActive == true) return
        observedCategoryId = categoryId
        streamsJob?.cancel()
        streamsJob = viewModelScope.launch {
            _state.update { it.copy(isLoadingStreams = it.streams.isEmpty(), error = null) }
            // Voir VodViewModel : délai jusqu'à la première émission de Room,
            // pour séparer une requête lente d'un rendu lent.
            val subscribedAt = System.nanoTime()
            var firstEmission = true
            getLiveStreamsUseCase(categoryId).collect { streams ->
                if (firstEmission) {
                    firstEmission = false
                    com.cstv.app.di.IptvLog.d(
                        "PERF",
                        "Live première émission catégorie=$categoryId flux=${streams.size} " +
                            "en ${(System.nanoTime() - subscribedAt) / 1_000_000}ms"
                    )
                }
                _state.update { it.copy(streams = streams, isLoadingStreams = false) }
                refreshCategoryCounts()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            catalogSyncManager.syncNow(com.cstv.app.domain.sync.SyncTrigger.MANUAL)
        }
    }

    /**
     * Un flux Live n'est jamais téléchargeable : hors ligne, la lecture est
     * toujours refusée, avec un message qui le dit plutôt qu'un échec du player.
     */
    fun requestPlayback(onAllowed: () -> Unit) {
        viewModelScope.launch {
            when (canPlayContentUseCase(null)) {
                com.cstv.app.domain.usecase.PlaybackAvailability.Allowed -> onAllowed()
                com.cstv.app.domain.usecase.PlaybackAvailability.RequiresConnection ->
                    _state.update { it.copy(error = com.cstv.app.presentation.OFFLINE_PLAYBACK_MESSAGE) }
                com.cstv.app.domain.usecase.PlaybackAvailability.RequiresReauthentication ->
                    _state.update { it.copy(error = com.cstv.app.presentation.REAUTHENTICATION_MESSAGE) }
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
