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

    @Mock private lateinit var canPlayContentUseCase: com.cstv.app.domain.usecase.CanPlayContentUseCase
    @Mock private lateinit var observeCatalogStatusUseCase: com.cstv.app.domain.usecase.ObserveCatalogStatusUseCase
    @Mock private lateinit var catalogSyncManager: com.cstv.app.domain.sync.CatalogSyncManager
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
    @Mock private lateinit var getTrailerPreviewUseCase: GetTrailerPreviewUseCase
    @Mock private lateinit var invalidateTrailerPreviewUseCase: InvalidateTrailerPreviewUseCase

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: SeriesViewModel

    @Before
    fun setUp() = runTest(testDispatcher) {
        MockitoAnnotations.openMocks(this@SeriesViewModelTest)
        Dispatchers.setMain(testDispatcher)

        whenever(getSeriesCategoriesUseCase()).thenReturn(flowOf(listOf(SeriesCategory("all", "Tout", 0))))
        whenever(getSeriesStreamsUseCase(any())).thenReturn(flowOf(emptyList()))
        whenever(categoryPreferenceRepository.changes).thenReturn(flowOf(Unit))
        whenever(observeCatalogStatusUseCase()).thenReturn(flowOf(com.cstv.app.domain.sync.CatalogStatus()))
        whenever(categoryPreferenceRepository.getPreferences(any())).thenReturn(emptyMap())
        whenever(seriesRepository.getCachedSeriesStreams(any())).thenReturn(emptyList())
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
            setMediaRatingUseCase,
            observeCatalogStatusUseCase,
            catalogSyncManager,
            canPlayContentUseCase,
            getTrailerPreviewUseCase,
            invalidateTrailerPreviewUseCase
        )
        runCurrent()

        val state = viewModel.state.value
        assertEquals(1, state.resumeSeries.size)
        assertEquals(1001, state.resumeSeries[0].seriesId)
        assertTrue(viewModel.selectCategoryById("all"))
        assertEquals("all", viewModel.state.value.selectedCategory?.categoryId)
        assertFalse(viewModel.selectCategoryById("unknown"))
        assertEquals("all", viewModel.state.value.selectedCategory?.categoryId)
    }

    @Test
    fun selectingAnAlreadyLoadedDetailDoesNotReloadIt() = runTest(testDispatcher) {
        whenever(vodRepository.observeAllPlaybackPositions()).thenReturn(flowOf(emptyList()))
        whenever(mediaRatingRepository.observeRating(42, RatedMediaType.SERIES)).thenReturn(flowOf(null))
        whenever(getSeriesDetailsUseCase(42)).thenReturn(
            SeriesDetails(42, "Series", null, null, emptyList(), emptyMap())
        )
        viewModel = SeriesViewModel(getSeriesCategoriesUseCase, getSeriesCategoryCountsUseCase, getSeriesStreamsUseCase,
            getSeriesDetailsUseCase, getRelatedSeriesUseCase, savePlaybackPositionUseCase, credentialsManager,
            settingsManager, trackPreferenceRepository, categoryPreferenceRepository, vodRepository, seriesRepository,
            removeFromContinueWatchingUseCase, mediaRatingRepository, setMediaRatingUseCase,
            observeCatalogStatusUseCase, catalogSyncManager, canPlayContentUseCase, getTrailerPreviewUseCase, invalidateTrailerPreviewUseCase)
        runCurrent()

        viewModel.selectStreamId(42)
        runCurrent()
        viewModel.selectStreamId(42)
        runCurrent()

        verify(getSeriesDetailsUseCase, times(1)).invoke(42)
    }

    @Test
    fun selectingAnotherDetailLoadsTheNewSeries() = runTest(testDispatcher) {
        whenever(vodRepository.observeAllPlaybackPositions()).thenReturn(flowOf(emptyList()))
        whenever(mediaRatingRepository.observeRating(any(), eq(RatedMediaType.SERIES))).thenReturn(flowOf(null))
        whenever(getSeriesDetailsUseCase(42)).thenReturn(SeriesDetails(42, "A", null, null, emptyList(), emptyMap()))
        whenever(getSeriesDetailsUseCase(43)).thenReturn(SeriesDetails(43, "B", null, null, emptyList(), emptyMap()))
        viewModel = SeriesViewModel(getSeriesCategoriesUseCase, getSeriesCategoryCountsUseCase, getSeriesStreamsUseCase,
            getSeriesDetailsUseCase, getRelatedSeriesUseCase, savePlaybackPositionUseCase, credentialsManager,
            settingsManager, trackPreferenceRepository, categoryPreferenceRepository, vodRepository, seriesRepository,
            removeFromContinueWatchingUseCase, mediaRatingRepository, setMediaRatingUseCase,
            observeCatalogStatusUseCase, catalogSyncManager, canPlayContentUseCase, getTrailerPreviewUseCase, invalidateTrailerPreviewUseCase)
        runCurrent()

        viewModel.selectStreamId(42)
        runCurrent()
        viewModel.selectStreamId(43)
        runCurrent()

        verify(getSeriesDetailsUseCase).invoke(43)
        assertEquals(43, viewModel.state.value.selectedSeriesDetails?.seriesId)
    }

    @Test
    fun failedDetailCanBeRetried() = runTest(testDispatcher) {
        whenever(vodRepository.observeAllPlaybackPositions()).thenReturn(flowOf(emptyList()))
        whenever(mediaRatingRepository.observeRating(42, RatedMediaType.SERIES)).thenReturn(flowOf(null))
        whenever(getSeriesDetailsUseCase(42)).thenThrow(RuntimeException("offline"))
            .thenReturn(SeriesDetails(42, "Recovered", null, null, emptyList(), emptyMap()))
        viewModel = SeriesViewModel(getSeriesCategoriesUseCase, getSeriesCategoryCountsUseCase, getSeriesStreamsUseCase,
            getSeriesDetailsUseCase, getRelatedSeriesUseCase, savePlaybackPositionUseCase, credentialsManager,
            settingsManager, trackPreferenceRepository, categoryPreferenceRepository, vodRepository, seriesRepository,
            removeFromContinueWatchingUseCase, mediaRatingRepository, setMediaRatingUseCase,
            observeCatalogStatusUseCase, catalogSyncManager, canPlayContentUseCase, getTrailerPreviewUseCase, invalidateTrailerPreviewUseCase)
        runCurrent()

        viewModel.selectStreamId(42)
        runCurrent()
        viewModel.selectStreamId(42)
        runCurrent()

        verify(getSeriesDetailsUseCase, times(2)).invoke(42)
        assertEquals("Recovered", viewModel.state.value.selectedSeriesDetails?.name)
    }

    @Test
    fun savePositionPersistsExplicitSeriesIdWithoutSelectedDetails() = runTest(testDispatcher) {
        whenever(vodRepository.observeAllPlaybackPositions()).thenReturn(flowOf(emptyList()))
        viewModel = SeriesViewModel(
            getSeriesCategoriesUseCase, getSeriesCategoryCountsUseCase, getSeriesStreamsUseCase,
            getSeriesDetailsUseCase, getRelatedSeriesUseCase, savePlaybackPositionUseCase, credentialsManager,
            settingsManager, trackPreferenceRepository, categoryPreferenceRepository, vodRepository, seriesRepository,
            removeFromContinueWatchingUseCase, mediaRatingRepository, setMediaRatingUseCase,
            observeCatalogStatusUseCase, catalogSyncManager, canPlayContentUseCase, getTrailerPreviewUseCase, invalidateTrailerPreviewUseCase
        )
        runCurrent()

        viewModel.savePosition(
            episode = SeriesEpisode(
                id = 501,
                episodeNum = 2,
                title = "Épisode",
                containerExtension = "mp4",
                plot = "",
                duration = "",
                releaseDate = "",
                seasonNum = 1
            ),
            positionMs = 1_000L,
            durationMs = 10_000L,
            seriesName = "Série",
            seriesCover = null,
            seriesId = 42
        )
        runCurrent()

        verify(savePlaybackPositionUseCase).invoke(
            streamId = eq(501),
            positionMs = eq(1_000L),
            durationMs = eq(10_000L),
            title = any(),
            coverUrl = isNull(),
            type = eq("series"),
            containerExtension = eq("mp4"),
            seriesId = eq(42),
            episodeNum = eq(2),
            seasonNum = eq(1),
            plot = eq(""),
            duration = eq(""),
            releaseDate = eq(""),
            categoryId = isNull()
        )
    }

    @Test
    fun savePositionFallsBackToSelectedSeriesDetailsId() = runTest(testDispatcher) {
        whenever(vodRepository.observeAllPlaybackPositions()).thenReturn(flowOf(emptyList()))
        whenever(mediaRatingRepository.observeRating(42, RatedMediaType.SERIES)).thenReturn(flowOf(null))
        whenever(getSeriesDetailsUseCase(42)).thenReturn(SeriesDetails(42, "Série", null, null, emptyList(), emptyMap()))
        viewModel = SeriesViewModel(
            getSeriesCategoriesUseCase, getSeriesCategoryCountsUseCase, getSeriesStreamsUseCase,
            getSeriesDetailsUseCase, getRelatedSeriesUseCase, savePlaybackPositionUseCase, credentialsManager,
            settingsManager, trackPreferenceRepository, categoryPreferenceRepository, vodRepository, seriesRepository,
            removeFromContinueWatchingUseCase, mediaRatingRepository, setMediaRatingUseCase,
            observeCatalogStatusUseCase, catalogSyncManager, canPlayContentUseCase, getTrailerPreviewUseCase, invalidateTrailerPreviewUseCase
        )
        runCurrent()
        viewModel.selectStreamId(42)
        runCurrent()

        viewModel.savePosition(
            episode = SeriesEpisode(
                id = 501,
                episodeNum = 2,
                title = "Épisode",
                containerExtension = "mp4",
                plot = "",
                duration = "",
                releaseDate = "",
                seasonNum = 1
            ),
            positionMs = 1_000L,
            durationMs = 10_000L,
            seriesName = "Série",
            seriesCover = null
        )
        runCurrent()

        verify(savePlaybackPositionUseCase).invoke(
            streamId = eq(501),
            positionMs = eq(1_000L),
            durationMs = eq(10_000L),
            title = any(),
            coverUrl = isNull(),
            type = eq("series"),
            containerExtension = eq("mp4"),
            seriesId = eq(42),
            episodeNum = eq(2),
            seasonNum = eq(1),
            plot = eq(""),
            duration = eq(""),
            releaseDate = eq(""),
            categoryId = isNull()
        )
    }

    // T7-R2 : entrer sur l'onglet Séries déclenche silencieusement syncIfStale().
    @Test
    fun `entering series tab silently triggers syncIfStale`() = runTest(testDispatcher) {
        whenever(vodRepository.observeAllPlaybackPositions()).thenReturn(flowOf(emptyList()))
        viewModel = SeriesViewModel(getSeriesCategoriesUseCase, getSeriesCategoryCountsUseCase, getSeriesStreamsUseCase,
            getSeriesDetailsUseCase, getRelatedSeriesUseCase, savePlaybackPositionUseCase, credentialsManager,
            settingsManager, trackPreferenceRepository, categoryPreferenceRepository, vodRepository, seriesRepository,
            removeFromContinueWatchingUseCase, mediaRatingRepository, setMediaRatingUseCase,
            observeCatalogStatusUseCase, catalogSyncManager, canPlayContentUseCase, getTrailerPreviewUseCase, invalidateTrailerPreviewUseCase)
        runCurrent()

        verify(catalogSyncManager).syncIfStale()
    }

    // T7-R2 : un échec de la synchronisation silencieuse ne doit jamais
    // remonter dans l'état UI (règle métier 4 de T7).
    @Test
    fun `a syncIfStale failure never propagates to the ui state`() = runTest(testDispatcher) {
        whenever(vodRepository.observeAllPlaybackPositions()).thenReturn(flowOf(emptyList()))
        whenever(catalogSyncManager.syncIfStale()).thenThrow(RuntimeException("panel injoignable"))
        viewModel = SeriesViewModel(getSeriesCategoriesUseCase, getSeriesCategoryCountsUseCase, getSeriesStreamsUseCase,
            getSeriesDetailsUseCase, getRelatedSeriesUseCase, savePlaybackPositionUseCase, credentialsManager,
            settingsManager, trackPreferenceRepository, categoryPreferenceRepository, vodRepository, seriesRepository,
            removeFromContinueWatchingUseCase, mediaRatingRepository, setMediaRatingUseCase,
            observeCatalogStatusUseCase, catalogSyncManager, canPlayContentUseCase, getTrailerPreviewUseCase, invalidateTrailerPreviewUseCase)
        runCurrent()

        verify(catalogSyncManager).syncIfStale()
        assertEquals(null, viewModel.state.value.selectedSeriesDetails)
    }
}
