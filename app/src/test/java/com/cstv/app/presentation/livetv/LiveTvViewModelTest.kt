package com.cstv.app.presentation.livetv

import com.cstv.app.data.local.storage.CredentialsManager
import com.cstv.app.data.local.storage.SettingsManager
import com.cstv.app.domain.model.LiveCategory
import com.cstv.app.domain.model.LiveStream
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import com.cstv.app.domain.repository.LiveTvRepository
import com.cstv.app.domain.usecase.GetLiveCategoriesUseCase
import com.cstv.app.domain.usecase.GetLiveCategoryCountsUseCase
import com.cstv.app.domain.usecase.GetLiveEpgNowNextUseCase
import com.cstv.app.domain.usecase.GetLiveEpgUseCase
import com.cstv.app.domain.usecase.GetLiveStreamsUseCase
import com.cstv.app.domain.usecase.ObserveRecentlyWatchedUseCase
import com.cstv.app.domain.usecase.RemoveRecentlyWatchedUseCase
import com.cstv.app.domain.usecase.SaveRecentlyWatchedUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class LiveTvViewModelTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (ticker infini dans un `init` de ViewModel, `advanceUntilIdle` sur une
    // tâche périodique) fige le build sans jamais échouer. Cette règle nomme le
    // test fautif ; le garde-fou dur est `tasks.withType<Test> { timeout }`
    // dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)

    private val dispatcher = StandardTestDispatcher()
    private val getCategories: GetLiveCategoriesUseCase = mock()
    private val getCategoryCounts: GetLiveCategoryCountsUseCase = mock()
    private val getStreams: GetLiveStreamsUseCase = mock()
    private val observeRecentlyWatched: ObserveRecentlyWatchedUseCase = mock()
    private val removeRecentlyWatched: RemoveRecentlyWatchedUseCase = mock()
    private val saveRecentlyWatched: SaveRecentlyWatchedUseCase = mock()
    private val getEpg: GetLiveEpgUseCase = mock()
    private val getEpgNowNext: GetLiveEpgNowNextUseCase = mock()
    private val credentialsManager: CredentialsManager = mock()
    private val categoryPreferences: CategoryPreferenceRepository = mock()
    private val settingsManager: SettingsManager = mock()
    private val liveTvRepository: LiveTvRepository = mock()
    private val observeCatalogStatusUseCase: com.cstv.app.domain.usecase.ObserveCatalogStatusUseCase = mock()
    private val catalogSyncManager: com.cstv.app.domain.sync.CatalogSyncManager = mock()
    private val canPlayContentUseCase: com.cstv.app.domain.usecase.CanPlayContentUseCase = mock()

    @Before
    fun setUp() = runTest(dispatcher) {
        MockitoAnnotations.openMocks(this@LiveTvViewModelTest)
        Dispatchers.setMain(dispatcher)
        whenever(getCategories()).thenReturn(flowOf(listOf(LiveCategory("all", "Tout", 0))))
        whenever(getStreams(any())).thenReturn(flowOf(emptyList()))
        whenever(getCategoryCounts()).thenReturn(emptyMap())
        whenever(observeRecentlyWatched()).thenReturn(flowOf(emptyList()))
        whenever(categoryPreferences.changes).thenReturn(flowOf(Unit))
        whenever(observeCatalogStatusUseCase()).thenReturn(flowOf(com.cstv.app.domain.sync.CatalogStatus()))
        // Voir VodViewModelTest.
        whenever(catalogSyncManager.syncState)
            .thenReturn(kotlinx.coroutines.flow.MutableStateFlow(com.cstv.app.domain.sync.SyncState.Idle))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `remove recent live item clears loading state after success`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        val stream = LiveStream(42, "News", null, null, 1, "5")

        viewModel.removeRecentlyWatched(stream)
        advanceUntilIdle()

        verify(removeRecentlyWatched)(42)
        assertFalse(viewModel.state.value.isRemovingHistory)
        assertEquals(null, viewModel.state.value.historyRemovalError)
    }

    @Test
    fun `remove recent live item exposes and consumes a generic failure`() = runTest(dispatcher) {
        whenever(removeRecentlyWatched(42)).thenThrow(IllegalStateException("Room failure"))
        val viewModel = createViewModel()

        viewModel.removeRecentlyWatched(LiveStream(42, "News", null, null, 1, "5"))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isRemovingHistory)
        assertEquals("Impossible de retirer cet élément. Réessayez.", viewModel.state.value.historyRemovalError)
        viewModel.consumeHistoryRemovalError()
        assertEquals(null, viewModel.state.value.historyRemovalError)
    }

    // T7-R2 : entrer sur l'onglet Live TV déclenche silencieusement syncIfStale().
    @Test
    fun `entering live tv silently triggers syncIfStale`() = runTest(dispatcher) {
        createViewModel()
        advanceUntilIdle()

        verify(catalogSyncManager).syncIfStale()
    }

    // T7-R2 : un échec de la synchronisation silencieuse ne doit jamais
    // remonter dans l'état UI (règle métier 4 de T7) ni empêcher le reste de
    // l'initialisation de l'écran.
    @Test
    fun `a syncIfStale failure never propagates to the ui state`() = runTest(dispatcher) {
        whenever(catalogSyncManager.syncIfStale()).thenThrow(RuntimeException("panel injoignable"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        verify(catalogSyncManager).syncIfStale()
        assertEquals(null, viewModel.state.value.historyRemovalError)
        assertFalse(viewModel.state.value.isRemovingHistory)
    }

    @Test
    fun `playback conflict stays in ViewModel until explicit takeover`() = runTest(dispatcher) {
        val requester: com.cstv.app.domain.usecase.PlaybackLockRequester = mock()
        val holder = com.cstv.app.domain.model.PlaybackLockHolder("Salon", 42)
        whenever(requester.request(null, false)).thenReturn(com.cstv.app.domain.usecase.PlaybackLockRequestResult.Held(holder))
        whenever(requester.request(null, true)).thenReturn(com.cstv.app.domain.usecase.PlaybackLockRequestResult.Allowed)
        val viewModel = createViewModel(requester)

        viewModel.beginPlaybackLock()
        advanceUntilIdle()
        assertEquals(holder, viewModel.playbackLockUiState.value.conflict)
        assertFalse(viewModel.playbackLockUiState.value.canStartPlayback)

        viewModel.takeOverPlaybackLock()
        advanceUntilIdle()
        verify(requester).request(null, true)
        assertTrue(viewModel.playbackLockUiState.value.canStartPlayback)
    }

    // Retour utilisateur du 2026-08-18 : un repli automatique mémorisé pour une chaîne est réutilisé
    // au zapping suivant plutôt que de retenter systématiquement la meilleure qualité.
    @Test
    fun `retour utilisateur 2026-08-18 - a memorized downgrade is reused on the next zap`() = runTest(dispatcher) {
        whenever(settingsManager.getLiveQualityModeDefault()).thenReturn(true)
        val top = LiveStream(1, "TF1 4K", null, null, 1, "1", linkKey = "tf1", qualityTag = "uhd_4k")
        val lower = LiveStream(2, "TF1 HD", null, null, 1, "1", linkKey = "tf1", qualityTag = "hd")
        val variantRepository = object : com.cstv.app.domain.repository.LiveVariantRepository {
            override suspend fun variantsFor(streamId: Int) = listOf(
                com.cstv.app.domain.model.LiveVariant(top),
                com.cstv.app.domain.model.LiveVariant(lower)
            )
        }
        // `LiveQualityDowngradeMemory` est persisté (SharedPreferences) : simple double en
        // mémoire ici, la classe réelle est couverte par LiveQualityDowngradeMemoryTest.
        val memory: com.cstv.app.presentation.player.core.LiveQualityDowngradeMemory = mock()
        whenever(memory.rememberedStreamId(eq("tf1"), any())).thenReturn(lower.streamId)
        val viewModel = createViewModel(liveVariantRepository = variantRepository, qualityDowngradeMemory = memory)

        val state = viewModel.startLiveQualitySession(top)

        assertEquals(lower.streamId, state.selectedStream.streamId)
    }

    private fun createViewModel(
        requester: com.cstv.app.domain.usecase.PlaybackLockRequester? = null,
        liveVariantRepository: com.cstv.app.domain.repository.LiveVariantRepository? = null,
        qualityDowngradeMemory: com.cstv.app.presentation.player.core.LiveQualityDowngradeMemory? = null
    ) = LiveTvViewModel(
        getCategories, getCategoryCounts, getStreams, observeRecentlyWatched,
        removeRecentlyWatched, saveRecentlyWatched, getEpg, getEpgNowNext,
        credentialsManager, categoryPreferences, settingsManager, liveTvRepository,
        observeCatalogStatusUseCase, catalogSyncManager, canPlayContentUseCase,
        requester,
        releasePlaybackLockUseCase = null,
        playbackLockManager = null,
        playbackRepairRepository = null,
        liveVariantRepository = liveVariantRepository ?: com.cstv.app.presentation.livetv.EmptyLiveVariantRepository,
        qualityDowngradeMemory = qualityDowngradeMemory
    )
}
