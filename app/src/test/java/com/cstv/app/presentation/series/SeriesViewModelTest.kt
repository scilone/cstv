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
import org.junit.Rule
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class SeriesViewModelTest {
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
    @Mock private lateinit var seriesVersionPreferenceRepository: com.cstv.app.domain.repository.SeriesVersionPreferenceRepository
    @Mock private lateinit var invalidateTrailerPreviewUseCase: InvalidateTrailerPreviewUseCase
    @Mock private lateinit var getRecommendationsUseCase: GetRecommendationsUseCase

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
        // Voir VodViewModelTest.
        whenever(catalogSyncManager.syncState)
            .thenReturn(kotlinx.coroutines.flow.MutableStateFlow(com.cstv.app.domain.sync.SyncState.Idle))
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
            PlaybackPosition(201, 1000L, 50000L, System.currentTimeMillis(), "Episode 201", "cover2", "episode", "mp4", seriesId = 1001)
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
            invalidateTrailerPreviewUseCase,
            getRecommendationsUseCase,
            testDispatcher
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
            observeCatalogStatusUseCase, catalogSyncManager, canPlayContentUseCase, getTrailerPreviewUseCase, invalidateTrailerPreviewUseCase, getRecommendationsUseCase, testDispatcher)
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
            observeCatalogStatusUseCase, catalogSyncManager, canPlayContentUseCase, getTrailerPreviewUseCase, invalidateTrailerPreviewUseCase, getRecommendationsUseCase, testDispatcher)
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
            observeCatalogStatusUseCase, catalogSyncManager, canPlayContentUseCase, getTrailerPreviewUseCase, invalidateTrailerPreviewUseCase, getRecommendationsUseCase, testDispatcher)
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
            observeCatalogStatusUseCase, catalogSyncManager, canPlayContentUseCase, getTrailerPreviewUseCase, invalidateTrailerPreviewUseCase,
            getRecommendationsUseCase,
            testDispatcher
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

        // T20: title/cover/type/containerExtension/series info/plot/duration/releaseDate/categoryId
        // are resolved from the catalogue at display time -- no longer passed to the use case.
        verify(savePlaybackPositionUseCase).invoke(
            kind = eq("episode"),
            providerId = eq(501),
            positionMs = eq(1_000L),
            durationMs = eq(10_000L)
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
            observeCatalogStatusUseCase, catalogSyncManager, canPlayContentUseCase, getTrailerPreviewUseCase, invalidateTrailerPreviewUseCase,
            getRecommendationsUseCase,
            testDispatcher
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

        // T20: title/cover/type/containerExtension/series info/plot/duration/releaseDate/categoryId
        // are resolved from the catalogue at display time -- no longer passed to the use case.
        verify(savePlaybackPositionUseCase).invoke(
            kind = eq("episode"),
            providerId = eq(501),
            positionMs = eq(1_000L),
            durationMs = eq(10_000L)
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
            observeCatalogStatusUseCase, catalogSyncManager, canPlayContentUseCase, getTrailerPreviewUseCase, invalidateTrailerPreviewUseCase, getRecommendationsUseCase, testDispatcher)
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
            observeCatalogStatusUseCase, catalogSyncManager, canPlayContentUseCase, getTrailerPreviewUseCase, invalidateTrailerPreviewUseCase, getRecommendationsUseCase, testDispatcher)
        runCurrent()

        verify(catalogSyncManager).syncIfStale()
        assertEquals(null, viewModel.state.value.selectedSeriesDetails)
    }

    // --- F22 : filtres avancés portés par la catégorie TV active ---

    private fun series(seriesId: Int, rating: String?, year: Int?, genre: String?) =
        SeriesStream(seriesId, "Série $seriesId", null, rating, null, "10", genre, year)

    /**
     * Catalogue à deux catégories : « Action » (2000 et 2020) et « Comédie »
     * (2010). Les bornes et genres attendus diffèrent d'une catégorie à l'autre,
     * ce qui rend visible toute fuite d'état entre elles.
     */
    private suspend fun createViewModelWithCategories(): SeriesViewModel {
        whenever(getSeriesCategoriesUseCase()).thenReturn(
            flowOf(listOf(SeriesCategory("10", "Action", 0), SeriesCategory("20", "Comédie", 0)))
        )
        whenever(getSeriesStreamsUseCase("10")).thenReturn(
            flowOf(
                listOf(
                    series(1, "8.0", 2000, "Action, Drame"),
                    series(2, "3.0", 2020, "Comédie")
                )
            )
        )
        whenever(getSeriesStreamsUseCase("20")).thenReturn(flowOf(listOf(series(3, "6.0", 2010, "Comédie"))))
        whenever(vodRepository.observeAllPlaybackPositions()).thenReturn(flowOf(emptyList()))
        return createViewModel()
    }

    @Test
    fun `changing category resets the advanced filter and closes the sheet`() = runTest(testDispatcher) {
        viewModel = createViewModelWithCategories()
        runCurrent()
        assertTrue(viewModel.selectCategoryById("10"))
        runCurrent()

        viewModel.setMinRating(4)
        viewModel.toggleGenre("Action")
        viewModel.setFilterSheetOpen(true)
        assertTrue(viewModel.state.value.advancedFilter.isActive)

        assertTrue(viewModel.selectCategoryById("20"))
        runCurrent()

        val state = viewModel.state.value
        assertEquals(AdvancedSearchFilter.DEFAULT, state.advancedFilter)
        assertFalse(state.isFilterSheetOpen)
        assertEquals(listOf("Comédie"), state.availableGenres)
        assertEquals(2010..2010, state.categoryYearRange)
    }

    @Test
    fun `toggleGenre adds then removes a genre and applyFilter only closes the sheet`() = runTest(testDispatcher) {
        viewModel = createViewModelWithCategories()
        runCurrent()
        viewModel.selectCategoryById("10")
        runCurrent()

        viewModel.toggleGenre("Action")
        assertEquals(setOf("Action"), viewModel.state.value.advancedFilter.genres)
        viewModel.toggleGenre("Action")
        assertTrue(viewModel.state.value.advancedFilter.genres.isEmpty())

        viewModel.setMinRating(4)
        viewModel.setFilterSheetOpen(true)
        viewModel.applyFilter()
        assertFalse(viewModel.state.value.isFilterSheetOpen)
        assertEquals(4, viewModel.state.value.advancedFilter.minRating)
    }

    @Test
    fun `setYearRange keeps a narrowing range and drops one covering the whole category`() = runTest(testDispatcher) {
        viewModel = createViewModelWithCategories()
        runCurrent()
        viewModel.selectCategoryById("10")
        runCurrent()
        assertEquals(2000..2020, viewModel.state.value.categoryYearRange)

        viewModel.setYearRange(2010..2020)
        assertEquals(2010..2020, viewModel.state.value.advancedFilter.yearRange)

        viewModel.setYearRange(1990..2030)
        assertNull(viewModel.state.value.advancedFilter.yearRange)
    }

    @Test
    fun `removing one filter leaves the other criteria untouched`() = runTest(testDispatcher) {
        viewModel = createViewModelWithCategories()
        runCurrent()
        viewModel.selectCategoryById("10")
        runCurrent()

        viewModel.setMinRating(4)
        viewModel.setYearRange(2010..2020)
        viewModel.toggleGenre("Comédie")

        viewModel.removeMinRatingFilter()
        var filter = viewModel.state.value.advancedFilter
        assertNull(filter.minRating)
        assertEquals(2010..2020, filter.yearRange)
        assertEquals(setOf("Comédie"), filter.genres)

        viewModel.removeGenreFilter("Comédie")
        filter = viewModel.state.value.advancedFilter
        assertTrue(filter.genres.isEmpty())
        assertEquals(2010..2020, filter.yearRange)

        viewModel.removeYearRangeFilter()
        assertTrue(viewModel.state.value.advancedFilter.isEmpty)
    }

    @Test
    fun `filteredCount and available genres follow the active category filter`() = runTest(testDispatcher) {
        viewModel = createViewModelWithCategories()
        runCurrent()
        viewModel.selectCategoryById("10")
        runCurrent()

        val loaded = viewModel.state.value
        assertEquals(listOf("Action", "Comédie", "Drame"), loaded.availableGenres)
        assertEquals(2, loaded.filteredCount)

        viewModel.setMinRating(4)
        assertEquals(1, viewModel.state.value.filteredCount)

        // La seule série « Comédie » est notée 3.0 : les critères se cumulent.
        viewModel.toggleGenre("Comédie")
        assertEquals(0, viewModel.state.value.filteredCount)

        viewModel.resetFilter()
        assertTrue(viewModel.state.value.advancedFilter.isEmpty)
        assertEquals(2, viewModel.state.value.filteredCount)
    }

    // --- F39 §8.6, tâche 5d : versions candidates d'un épisode ---

    @Test
    fun `getEpisodeVersions returns an empty list when the resolver was not injected`() = runTest(testDispatcher) {
        whenever(vodRepository.observeAllPlaybackPositions()).thenReturn(flowOf(emptyList()))
        val viewModel = createViewModel() // pas de resolver, comme les tests existants ci-dessus

        assertTrue(viewModel.getEpisodeVersions(seriesId = 1, seasonNum = 1, episodeNum = 1).isEmpty())
    }

    @Test
    fun `getEpisodeVersions returns an empty list when the series no longer exists in cache`() = runTest(testDispatcher) {
        whenever(seriesRepository.getStreamById(1)).thenReturn(null)
        val viewModel = createViewModelWithVersions()

        assertTrue(viewModel.getEpisodeVersions(seriesId = 1, seasonNum = 1, episodeNum = 1).isEmpty())
    }

    @Test
    fun `getEpisodeVersions returns an empty list when linkKey is not yet normalized`() = runTest(testDispatcher) {
        whenever(seriesRepository.getStreamById(1)).thenReturn(seriesStream(1, linkKey = ""))
        val viewModel = createViewModelWithVersions()

        assertTrue(viewModel.getEpisodeVersions(seriesId = 1, seasonNum = 1, episodeNum = 1).isEmpty())
    }

    @Test
    fun `getEpisodeVersions filters out a candidate without the equivalent episode`() = runTest(testDispatcher) {
        val opened = seriesStream(1, linkKey = "key-a")
        val incomplete = seriesStream(2, linkKey = "key-a")
        whenever(seriesRepository.getStreamById(1)).thenReturn(opened)
        whenever(seriesRepository.getVersionsByLinkKey("key-a", null)).thenReturn(listOf(opened, incomplete))
        whenever(seriesRepository.getEpisodeBySeasonEpisode(1, 1, 1)).thenReturn(seriesEpisode(101))
        whenever(seriesRepository.getEpisodeBySeasonEpisode(2, 1, 1)).thenReturn(null)
        val viewModel = createViewModelWithVersions()

        val result = viewModel.getEpisodeVersions(seriesId = 1, seasonNum = 1, episodeNum = 1)

        assertEquals(1, result.size)
        assertEquals(opened, result.single().series)
    }

    // --- F39 §8.6, tâche 6a : versions candidates pour la fiche (sans filtre épisode) ---

    @Test
    fun `getSeriesVersions returns an empty list when the series no longer exists in cache`() = runTest(testDispatcher) {
        whenever(seriesRepository.getStreamById(1)).thenReturn(null)
        val viewModel = createViewModelWithVersions()

        assertTrue(viewModel.getSeriesVersions(1).isEmpty())
    }

    @Test
    fun `getSeriesVersions returns an empty list when linkKey is not yet normalized`() = runTest(testDispatcher) {
        whenever(seriesRepository.getStreamById(1)).thenReturn(seriesStream(1, linkKey = ""))
        val viewModel = createViewModelWithVersions()

        assertTrue(viewModel.getSeriesVersions(1).isEmpty())
    }

    @Test
    fun `getSeriesVersions queries by linkKey, including the current entry, without filtering by episode`() = runTest(testDispatcher) {
        val current = seriesStream(1, linkKey = "key-a")
        val other = seriesStream(2, linkKey = "key-a")
        whenever(seriesRepository.getStreamById(1)).thenReturn(current)
        whenever(seriesRepository.getVersionsByLinkKey("key-a", null)).thenReturn(listOf(current, other))
        val viewModel = createViewModelWithVersions()

        val result = viewModel.getSeriesVersions(1)

        assertEquals(listOf(current, other), result)
        // Contrairement à getEpisodeVersions : aucun appel à getEpisodeBySeasonEpisode.
        verify(seriesRepository, never()).getEpisodeBySeasonEpisode(any(), any(), any())
    }

    @Test
    fun `setPreferredSeriesVersion commits through the preference repository`() = runTest(testDispatcher) {
        val viewModel = createViewModelWithVersions()

        viewModel.setPreferredSeriesVersion("key-a", preferredSeriesId = 2)
        advanceUntilIdle()

        verify(seriesVersionPreferenceRepository).setPreference("key-a", 2)
    }

    private fun seriesStream(seriesId: Int, linkKey: String) = SeriesStream(
        seriesId = seriesId, name = "Série $seriesId", cover = null, rating = null, added = null,
        categoryId = "1", linkKey = linkKey
    )

    private fun seriesEpisode(id: Int) = SeriesEpisode(
        id = id, episodeNum = 1, title = "E1", containerExtension = "mkv",
        plot = "", duration = "", releaseDate = "", seasonNum = 1
    )

    private fun createViewModelWithVersions(): SeriesViewModel {
        whenever(vodRepository.observeAllPlaybackPositions()).thenReturn(flowOf(emptyList()))
        return createRawViewModelWithVersions()
    }

    private fun createRawViewModelWithVersions() = SeriesViewModel(
        getSeriesCategoriesUseCase, getSeriesCategoryCountsUseCase, getSeriesStreamsUseCase,
        getSeriesDetailsUseCase, getRelatedSeriesUseCase, savePlaybackPositionUseCase, credentialsManager,
        settingsManager, trackPreferenceRepository, categoryPreferenceRepository, vodRepository, seriesRepository,
        removeFromContinueWatchingUseCase, mediaRatingRepository, setMediaRatingUseCase,
        observeCatalogStatusUseCase, catalogSyncManager, canPlayContentUseCase, getTrailerPreviewUseCase,
        invalidateTrailerPreviewUseCase,
        getRecommendationsUseCase,
        testDispatcher,
        markPlaybackSyncUseCase = null,
        requestPlaybackLockUseCase = null,
        releasePlaybackLockUseCase = null,
        playbackLockManager = null,
        playbackRepairRepository = null,
        seriesVersionResolver = com.cstv.app.domain.model.SeriesVersionResolver(seriesRepository, seriesVersionPreferenceRepository),
        seriesVersionPreferenceRepository = seriesVersionPreferenceRepository
    )

    private fun createViewModel() = SeriesViewModel(
        getSeriesCategoriesUseCase, getSeriesCategoryCountsUseCase, getSeriesStreamsUseCase,
        getSeriesDetailsUseCase, getRelatedSeriesUseCase, savePlaybackPositionUseCase, credentialsManager,
        settingsManager, trackPreferenceRepository, categoryPreferenceRepository, vodRepository, seriesRepository,
        removeFromContinueWatchingUseCase, mediaRatingRepository, setMediaRatingUseCase,
        observeCatalogStatusUseCase, catalogSyncManager, canPlayContentUseCase, getTrailerPreviewUseCase,
        invalidateTrailerPreviewUseCase,
        getRecommendationsUseCase,
        testDispatcher
    )
}
