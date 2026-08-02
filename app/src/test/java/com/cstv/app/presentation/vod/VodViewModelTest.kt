package com.cstv.app.presentation.vod

import com.cstv.app.data.local.storage.CredentialsManager
import com.cstv.app.data.local.storage.SettingsManager
import com.cstv.app.domain.model.*
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import com.cstv.app.domain.repository.TrackPreferenceRepository
import com.cstv.app.domain.repository.VodRepository
import com.cstv.app.domain.usecase.*
import com.cstv.app.presentation.components.TrailerPreviewUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Rule
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class VodViewModelTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (ticker infini dans un `init` de ViewModel, `advanceUntilIdle` sur une
    // tâche périodique) fige le build sans jamais échouer. Cette règle nomme le
    // test fautif ; le garde-fou dur est `tasks.withType<Test> { timeout }`
    // dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)


    @Mock private lateinit var canPlayContentUseCase: com.cstv.app.domain.usecase.CanPlayContentUseCase
    @Mock private lateinit var observeCatalogStatusUseCase: com.cstv.app.domain.usecase.ObserveCatalogStatusUseCase
    @Mock private lateinit var catalogSyncManager: com.cstv.app.domain.sync.CatalogSyncManager
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
    @Mock private lateinit var mediaRatingRepository: com.cstv.app.domain.repository.MediaRatingRepository
    @Mock private lateinit var setMediaRatingUseCase: com.cstv.app.domain.usecase.SetMediaRatingUseCase
    @Mock private lateinit var getTrailerPreviewUseCase: GetTrailerPreviewUseCase
    @Mock private lateinit var invalidateTrailerPreviewUseCase: InvalidateTrailerPreviewUseCase

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: VodViewModel

    @Before
    fun setUp() = runTest(testDispatcher) {
        MockitoAnnotations.openMocks(this@VodViewModelTest)
        Dispatchers.setMain(testDispatcher)

        whenever(getVodCategoriesUseCase()).thenReturn(flowOf(listOf(VodCategory("all", "Tout", 0))))
        whenever(getVodStreamsUseCase(any())).thenReturn(flowOf(emptyList()))
        whenever(categoryPreferenceRepository.changes).thenReturn(flowOf(Unit))
        whenever(observeCatalogStatusUseCase()).thenReturn(flowOf(com.cstv.app.domain.sync.CatalogStatus()))
        whenever(categoryPreferenceRepository.getPreferences(any())).thenReturn(emptyMap())
        whenever(vodRepository.getCachedVodStreams(any())).thenReturn(emptyList())
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
        assertEquals(1, state.resumeMovies.size)
        assertEquals(101, state.resumeMovies[0].streamId)
        assertTrue(viewModel.selectCategoryById("all"))
        assertEquals("all", viewModel.state.value.selectedCategory?.categoryId)
        assertFalse(viewModel.selectCategoryById("unknown"))
        assertEquals("all", viewModel.state.value.selectedCategory?.categoryId)
    }

    @Test
    fun selectingAnAlreadyLoadedDetailDoesNotReloadIt() = runTest(testDispatcher) {
        whenever(vodRepository.observeAllPlaybackPositions()).thenReturn(flowOf(emptyList()))
        whenever(mediaRatingRepository.observeRating(42, RatedMediaType.MOVIE)).thenReturn(flowOf(null))
        whenever(getVodDetailsUseCase(42)).thenReturn(
            VodDetails(42, "Movie", "", "", "", "", "", "", null, "mp4")
        )
        viewModel = VodViewModel(getVodCategoriesUseCase, getVodCategoryCountsUseCase, getVodStreamsUseCase,
            getVodDetailsUseCase, getRelatedMoviesUseCase, savePlaybackPositionUseCase, credentialsManager,
            settingsManager, trackPreferenceRepository, categoryPreferenceRepository, vodRepository,
            removeFromContinueWatchingUseCase, mediaRatingRepository, setMediaRatingUseCase,
            observeCatalogStatusUseCase, catalogSyncManager, canPlayContentUseCase, getTrailerPreviewUseCase, invalidateTrailerPreviewUseCase)
        runCurrent()

        viewModel.selectStreamId(42)
        runCurrent()
        viewModel.selectStreamId(42)
        runCurrent()

        verify(getVodDetailsUseCase, times(1)).invoke(42)
    }

    @Test
    fun selectingAnotherDetailLoadsTheNewMovie() = runTest(testDispatcher) {
        whenever(vodRepository.observeAllPlaybackPositions()).thenReturn(flowOf(emptyList()))
        whenever(mediaRatingRepository.observeRating(any(), eq(RatedMediaType.MOVIE))).thenReturn(flowOf(null))
        whenever(getVodDetailsUseCase(42)).thenReturn(VodDetails(42, "A", "", "", "", "", "", "", null, "mp4"))
        whenever(getVodDetailsUseCase(43)).thenReturn(VodDetails(43, "B", "", "", "", "", "", "", null, "mp4"))
        viewModel = VodViewModel(getVodCategoriesUseCase, getVodCategoryCountsUseCase, getVodStreamsUseCase,
            getVodDetailsUseCase, getRelatedMoviesUseCase, savePlaybackPositionUseCase, credentialsManager,
            settingsManager, trackPreferenceRepository, categoryPreferenceRepository, vodRepository,
            removeFromContinueWatchingUseCase, mediaRatingRepository, setMediaRatingUseCase,
            observeCatalogStatusUseCase, catalogSyncManager, canPlayContentUseCase, getTrailerPreviewUseCase, invalidateTrailerPreviewUseCase)
        runCurrent()

        viewModel.selectStreamId(42)
        runCurrent()
        viewModel.selectStreamId(43)
        runCurrent()

        verify(getVodDetailsUseCase).invoke(43)
        assertEquals(43, viewModel.state.value.selectedVodDetails?.streamId)
    }

    @Test
    fun failedDetailCanBeRetried() = runTest(testDispatcher) {
        whenever(vodRepository.observeAllPlaybackPositions()).thenReturn(flowOf(emptyList()))
        whenever(mediaRatingRepository.observeRating(42, RatedMediaType.MOVIE)).thenReturn(flowOf(null))
        whenever(getVodDetailsUseCase(42)).thenThrow(RuntimeException("offline"))
            .thenReturn(VodDetails(42, "Recovered", "", "", "", "", "", "", null, "mp4"))
        viewModel = VodViewModel(getVodCategoriesUseCase, getVodCategoryCountsUseCase, getVodStreamsUseCase,
            getVodDetailsUseCase, getRelatedMoviesUseCase, savePlaybackPositionUseCase, credentialsManager,
            settingsManager, trackPreferenceRepository, categoryPreferenceRepository, vodRepository,
            removeFromContinueWatchingUseCase, mediaRatingRepository, setMediaRatingUseCase,
            observeCatalogStatusUseCase, catalogSyncManager, canPlayContentUseCase, getTrailerPreviewUseCase, invalidateTrailerPreviewUseCase)
        runCurrent()

        viewModel.selectStreamId(42)
        runCurrent()
        viewModel.selectStreamId(42)
        runCurrent()

        verify(getVodDetailsUseCase, times(2)).invoke(42)
        assertEquals("Recovered", viewModel.state.value.selectedVodDetails?.name)
    }

    @Test
    fun trailerPreview_transitionsFromPreparingToPlayingOrPoster() = runTest(testDispatcher) {
        val media = TrailerMedia.Movie(42, 100)
        val preview = TrailerPreview(media, TrailerSource.YouTube("dQw4w9WgXcQ"))
        whenever(vodRepository.observeAllPlaybackPositions()).thenReturn(flowOf(emptyList()))
        whenever(getTrailerPreviewUseCase(media)).thenReturn(preview)
        viewModel = createViewModel()

        viewModel.startTrailerPreview(media)
        assertEquals(TrailerPreviewUiState.Preparing, viewModel.state.value.trailerPreview)
        runCurrent()
        assertEquals(TrailerPreviewUiState.Playing(preview), viewModel.state.value.trailerPreview)

        val unavailable = TrailerMedia.Movie(43, 101)
        whenever(getTrailerPreviewUseCase(unavailable)).thenReturn(null)
        viewModel.startTrailerPreview(unavailable)
        runCurrent()
        assertEquals(TrailerPreviewUiState.Poster, viewModel.state.value.trailerPreview)
    }

    @Test
    fun trailerPreview_cancelsOnStreamChangeAndIgnoresOtherMediaFailure() = runTest(testDispatcher) {
        val media = TrailerMedia.Movie(42, 100)
        val preview = TrailerPreview(media, TrailerSource.YouTube("dQw4w9WgXcQ"))
        whenever(vodRepository.observeAllPlaybackPositions()).thenReturn(flowOf(emptyList()))
        whenever(getTrailerPreviewUseCase(media)).thenReturn(preview)
        whenever(mediaRatingRepository.observeRating(any(), eq(RatedMediaType.MOVIE))).thenReturn(flowOf(null))
        whenever(getVodDetailsUseCase(43)).thenReturn(VodDetails(43, "Other", "", "", "", "", "", "", null, "mp4"))
        viewModel = createViewModel()

        viewModel.startTrailerPreview(media)
        runCurrent()
        viewModel.reportTrailerPlaybackFailure(TrailerMedia.Movie(99, 999))
        assertEquals(TrailerPreviewUiState.Playing(preview), viewModel.state.value.trailerPreview)
        viewModel.reportTrailerPlaybackFailure(media)
        assertEquals(TrailerPreviewUiState.Failed, viewModel.state.value.trailerPreview)

        viewModel.selectStreamId(43)
        assertEquals(TrailerPreviewUiState.Poster, viewModel.state.value.trailerPreview)
    }

    @Test
    fun trailerPreview_latestRequestWinsWhenRequestsChangeBeforeResolution() = runTest(testDispatcher) {
        val first = TrailerMedia.Movie(42, 100)
        val second = TrailerMedia.Movie(43, 101)
        val secondPreview = TrailerPreview(second, TrailerSource.YouTube("9bZkp7q19f0"))
        whenever(vodRepository.observeAllPlaybackPositions()).thenReturn(flowOf(emptyList()))
        whenever(getTrailerPreviewUseCase(first)).thenReturn(TrailerPreview(first, TrailerSource.YouTube("dQw4w9WgXcQ")))
        whenever(getTrailerPreviewUseCase(second)).thenReturn(secondPreview)
        viewModel = createViewModel()

        viewModel.startTrailerPreview(first)
        viewModel.startTrailerPreview(second)
        runCurrent()

        assertEquals(TrailerPreviewUiState.Playing(secondPreview), viewModel.state.value.trailerPreview)
        verify(getTrailerPreviewUseCase, never()).invoke(first)
    }

    // T7-R2 : entrer sur l'onglet VOD déclenche silencieusement syncIfStale().
    @Test
    fun `entering vod tab silently triggers syncIfStale`() = runTest(testDispatcher) {
        whenever(vodRepository.observeAllPlaybackPositions()).thenReturn(flowOf(emptyList()))
        createViewModel()
        runCurrent()

        verify(catalogSyncManager).syncIfStale()
    }

    // T7-R2 : un échec de la synchronisation silencieuse ne doit jamais
    // remonter dans l'état UI (règle métier 4 de T7).
    @Test
    fun `a syncIfStale failure never propagates to the ui state`() = runTest(testDispatcher) {
        whenever(vodRepository.observeAllPlaybackPositions()).thenReturn(flowOf(emptyList()))
        whenever(catalogSyncManager.syncIfStale()).thenThrow(RuntimeException("panel injoignable"))
        val viewModel = createViewModel()
        runCurrent()

        verify(catalogSyncManager).syncIfStale()
        assertEquals(null, viewModel.state.value.selectedVodDetails)
    }

    private fun createViewModel() = VodViewModel(
        getVodCategoriesUseCase, getVodCategoryCountsUseCase, getVodStreamsUseCase,
        getVodDetailsUseCase, getRelatedMoviesUseCase, savePlaybackPositionUseCase, credentialsManager,
        settingsManager, trackPreferenceRepository, categoryPreferenceRepository, vodRepository,
        removeFromContinueWatchingUseCase, mediaRatingRepository, setMediaRatingUseCase,
        observeCatalogStatusUseCase, catalogSyncManager, canPlayContentUseCase, getTrailerPreviewUseCase, invalidateTrailerPreviewUseCase
    )
}
