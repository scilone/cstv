package com.cstv.app.presentation.vod

import com.cstv.app.data.local.storage.CredentialsManager
import com.cstv.app.data.local.storage.SettingsManager
import com.cstv.app.domain.model.*
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import com.cstv.app.domain.repository.TrackPreferenceRepository
import com.cstv.app.domain.repository.VodRepository
import com.cstv.app.domain.usecase.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class VodViewModelTest {

    @Mock private lateinit var getVodCategoriesUseCase: GetVodCategoriesUseCase
    @Mock private lateinit var getVodCategoryCountsUseCase: GetVodCategoryCountsUseCase
    @Mock private lateinit var getVodStreamsUseCase: GetVodStreamsUseCase
    @Mock private lateinit var getVodDetailsUseCase: GetVodDetailsUseCase
    @Mock private lateinit var getRelatedMoviesUseCase: GetRelatedMoviesUseCase
    @Mock private lateinit var savePlaybackPositionUseCase: SavePlaybackPositionUseCase
    @Mock private lateinit var credentialsManager: CredentialsManager
    @Mock private lateinit var settingsManager: SettingsManager
    @Mock private lateinit var trackPreferenceRepository: TrackPreferenceRepository
    @Mock private lateinit var categoryPreferenceRepository: CategoryPreferenceRepository
    @Mock private lateinit var vodRepository: VodRepository
    @Mock private lateinit var removeFromContinueWatchingUseCase: RemoveFromContinueWatchingUseCase

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: VodViewModel

    @Before
    fun setUp() = runTest(testDispatcher) {
        MockitoAnnotations.openMocks(this@VodViewModelTest)
        Dispatchers.setMain(testDispatcher)

        whenever(getVodCategoriesUseCase(any())).thenReturn(listOf(VodCategory("all", "Tout", 0)))
        whenever(getVodStreamsUseCase(any(), any())).thenReturn(emptyList())
        whenever(categoryPreferenceRepository.changes).thenReturn(flowOf(Unit))
        whenever(categoryPreferenceRepository.getPreferences(any())).thenReturn(emptyMap())
        whenever(vodRepository.getVodStreams(any(), any())).thenReturn(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun test_resumeMovies_observesAndFiltersCorrectly() = runTest(testDispatcher) {
        val positions = listOf(
            PlaybackPosition(101, 1000L, 50000L, System.currentTimeMillis(), "Movie 101", "cover1", "movie", "mp4"),
            PlaybackPosition(102, 1000L, 50000L, System.currentTimeMillis(), "Episode 102", "cover2", "series", "mp4")
        )

        whenever(vodRepository.observeAllPlaybackPositions()).thenReturn(flowOf(positions))
        
        viewModel = VodViewModel(
            getVodCategoriesUseCase,
            getVodCategoryCountsUseCase,
            getVodStreamsUseCase,
            getVodDetailsUseCase,
            getRelatedMoviesUseCase,
            savePlaybackPositionUseCase,
            credentialsManager,
            settingsManager,
            trackPreferenceRepository,
            categoryPreferenceRepository,
            vodRepository,
            removeFromContinueWatchingUseCase
        )
        runCurrent()

        val state = viewModel.state.value
        assertEquals(1, state.resumeMovies.size)
        assertEquals(101, state.resumeMovies[0].streamId)
    }
}
