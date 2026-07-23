package com.cstv.app.presentation.series

import com.cstv.app.data.local.storage.CredentialsManager
import com.cstv.app.data.local.storage.SettingsManager
import com.cstv.app.domain.model.*
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import com.cstv.app.domain.repository.TrackPreferenceRepository
import com.cstv.app.domain.repository.SeriesRepository
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
class SeriesViewModelTest {

    @Mock private lateinit var getSeriesCategoriesUseCase: GetSeriesCategoriesUseCase
    @Mock private lateinit var getSeriesCategoryCountsUseCase: GetSeriesCategoryCountsUseCase
    @Mock private lateinit var getSeriesStreamsUseCase: GetSeriesStreamsUseCase
    @Mock private lateinit var getSeriesDetailsUseCase: GetSeriesDetailsUseCase
    @Mock private lateinit var getRelatedSeriesUseCase: GetRelatedSeriesUseCase
    @Mock private lateinit var savePlaybackPositionUseCase: SavePlaybackPositionUseCase
    @Mock private lateinit var credentialsManager: CredentialsManager
    @Mock private lateinit var settingsManager: SettingsManager
    @Mock private lateinit var trackPreferenceRepository: TrackPreferenceRepository
    @Mock private lateinit var categoryPreferenceRepository: CategoryPreferenceRepository
    @Mock private lateinit var vodRepository: VodRepository
    @Mock private lateinit var seriesRepository: SeriesRepository
    @Mock private lateinit var removeFromContinueWatchingUseCase: RemoveFromContinueWatchingUseCase
    @Mock private lateinit var mediaRatingRepository: com.cstv.app.domain.repository.MediaRatingRepository
    @Mock private lateinit var setMediaRatingUseCase: com.cstv.app.domain.usecase.SetMediaRatingUseCase

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: SeriesViewModel

    @Before
    fun setUp() = runTest(testDispatcher) {
        MockitoAnnotations.openMocks(this@SeriesViewModelTest)
        Dispatchers.setMain(testDispatcher)

        whenever(getSeriesCategoriesUseCase(any())).thenReturn(listOf(SeriesCategory("all", "Tout", 0)))
        whenever(getSeriesStreamsUseCase(any(), any())).thenReturn(emptyList())
        whenever(categoryPreferenceRepository.changes).thenReturn(flowOf(Unit))
        whenever(categoryPreferenceRepository.getPreferences(any())).thenReturn(emptyMap())
        whenever(seriesRepository.getSeriesStreams(any(), any())).thenReturn(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun test_resumeSeries_observesAndFiltersCorrectly() = runTest(testDispatcher) {
        val positions = listOf(
            PlaybackPosition(101, 1000L, 50000L, System.currentTimeMillis(), "Movie 101", "cover1", "movie", "mp4"),
            PlaybackPosition(201, 1000L, 50000L, System.currentTimeMillis(), "Episode 201", "cover2", "series", "mp4", seriesId = 1001)
        )

        whenever(vodRepository.observeAllPlaybackPositions()).thenReturn(flowOf(positions))
        
        viewModel = SeriesViewModel(
            getSeriesCategoriesUseCase,
            getSeriesCategoryCountsUseCase,
            getSeriesStreamsUseCase,
            getSeriesDetailsUseCase,
            getRelatedSeriesUseCase,
            savePlaybackPositionUseCase,
            credentialsManager,
            settingsManager,
            trackPreferenceRepository,
            categoryPreferenceRepository,
            vodRepository,
            seriesRepository,
            removeFromContinueWatchingUseCase,
            mediaRatingRepository,
            setMediaRatingUseCase
        )
        runCurrent()

        val state = viewModel.state.value
        assertEquals(1, state.resumeSeries.size)
        assertEquals(1001, state.resumeSeries[0].seriesId)
    }
}
