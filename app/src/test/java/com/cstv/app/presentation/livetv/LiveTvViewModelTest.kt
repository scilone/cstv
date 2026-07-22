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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class LiveTvViewModelTest {
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

    @Before
    fun setUp() = runTest(dispatcher) {
        MockitoAnnotations.openMocks(this@LiveTvViewModelTest)
        Dispatchers.setMain(dispatcher)
        whenever(getCategories(any())).thenReturn(listOf(LiveCategory("all", "Tout", 0)))
        whenever(getStreams(any(), any())).thenReturn(emptyList())
        whenever(getCategoryCounts()).thenReturn(emptyMap())
        whenever(observeRecentlyWatched()).thenReturn(flowOf(emptyList()))
        whenever(categoryPreferences.changes).thenReturn(flowOf(Unit))
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

    private fun createViewModel() = LiveTvViewModel(
        getCategories, getCategoryCounts, getStreams, observeRecentlyWatched,
        removeRecentlyWatched, saveRecentlyWatched, getEpg, getEpgNowNext,
        credentialsManager, categoryPreferences, settingsManager, liveTvRepository
    )
}
