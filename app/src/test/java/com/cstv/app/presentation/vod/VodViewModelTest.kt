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
    @Mock private lateinit var getRecommendationsUseCase: GetRecommendationsUseCase

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
        // Le ViewModel observe la fin de synchronisation pour relancer
        // sa requête de liste (voir `observeSyncCompletion`).
        whenever(catalogSyncManager.syncState)
            .thenReturn(kotlinx.coroutines.flow.MutableStateFlow(com.cstv.app.domain.sync.SyncState.Idle))
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
            invalidateTrailerPreviewUseCase,
            getRecommendationsUseCase,
            testDispatcher
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
            observeCatalogStatusUseCase, catalogSyncManager, canPlayContentUseCase, getTrailerPreviewUseCase, invalidateTrailerPreviewUseCase, getRecommendationsUseCase, testDispatcher)
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
            observeCatalogStatusUseCase, catalogSyncManager, canPlayContentUseCase, getTrailerPreviewUseCase, invalidateTrailerPreviewUseCase, getRecommendationsUseCase, testDispatcher)
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
            observeCatalogStatusUseCase, catalogSyncManager, canPlayContentUseCase, getTrailerPreviewUseCase, invalidateTrailerPreviewUseCase, getRecommendationsUseCase, testDispatcher)
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

    /**
     * Re-sélectionner la catégorie déjà ouverte ne doit pas vider la
     * liste. Le garde de `observeStreams` renvoie sans se réabonner ; le vidage
     * laissait alors l'onglet « Tout » définitivement vide, jusqu'à la prochaine
     * écriture Room ou au redémarrage de l'application.
     */
    @Test
    fun `re-selecting the current category keeps the loaded streams`() = runTest(testDispatcher) {
        whenever(vodRepository.observeAllPlaybackPositions()).thenReturn(flowOf(emptyList()))
        whenever(getVodStreamsUseCase(any())).thenReturn(flowOf(listOf(movie(1, "5", 2020, "Action"))))
        val viewModel = createViewModel()
        runCurrent()
        assertEquals(1, viewModel.state.value.streams.size)

        viewModel.selectCategory(VodCategory("all", "Tout", 0))
        runCurrent()

        assertEquals(1, viewModel.state.value.streams.size)
    }

    /**
     * La fin d'une synchronisation relance la requête de liste.
     *
     * Au premier lancement, l'écran s'abonne à un catalogue vide et le
     * remplissage n'était signalé que par l'invalidation Room. Le flux `syncState`
     * fournit un second réveil, indépendant.
     */
    @Test
    fun `a successful sync re-subscribes the stream query`() = runTest(testDispatcher) {
        whenever(vodRepository.observeAllPlaybackPositions()).thenReturn(flowOf(emptyList()))
        val syncState = kotlinx.coroutines.flow.MutableStateFlow<com.cstv.app.domain.sync.SyncState>(
            com.cstv.app.domain.sync.SyncState.Idle
        )
        whenever(catalogSyncManager.syncState).thenReturn(syncState)
        // Catalogue vide au premier abonnement, rempli au second.
        whenever(getVodStreamsUseCase("all"))
            .thenReturn(flowOf(emptyList()))
            .thenReturn(flowOf(listOf(movie(1, "5", 2020, "Action"))))

        val viewModel = createViewModel()
        runCurrent()
        assertTrue(viewModel.state.value.streams.isEmpty())

        syncState.value = com.cstv.app.domain.sync.SyncState.Success(1_000L)
        runCurrent()

        assertEquals(1, viewModel.state.value.streams.size)
    }

    // --- F22 : filtres avancés portés par la catégorie TV active ---

    private fun movie(streamId: Int, rating: String?, year: Int?, genre: String?) =
        VodStream(streamId, "Film $streamId", null, rating, null, "10", genre, year)

    /**
     * Catalogue à deux catégories : « Action » (2000 et 2020) et « Comédie »
     * (2010). Les bornes et genres attendus diffèrent d'une catégorie à l'autre,
     * ce qui rend visible toute fuite d'état entre elles.
     */
    private suspend fun createViewModelWithCategories(): VodViewModel {
        whenever(getVodCategoriesUseCase()).thenReturn(
            flowOf(listOf(VodCategory("10", "Action", 0), VodCategory("20", "Comédie", 0)))
        )
        whenever(getVodStreamsUseCase("10")).thenReturn(
            flowOf(
                listOf(
                    movie(1, "8.0", 2000, "Action, Drame"),
                    movie(2, "3.0", 2020, "Comédie")
                )
            )
        )
        whenever(getVodStreamsUseCase("20")).thenReturn(flowOf(listOf(movie(3, "6.0", 2010, "Comédie"))))
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
        // Genres et bornes suivent la catégorie active, pas le catalogue complet :
        // proposer un genre absent de la catégorie donnerait toujours zéro résultat.
        assertEquals(listOf("Comédie"), state.availableGenres)
        assertEquals(2010..2010, state.categoryYearRange)
    }

    @Test
    fun `the all tab leaves the filter facets neutral`() = runTest(testDispatcher) {
        // Le panneau de filtres n'y est pas accessible : les dériver reviendrait
        // à parcourir tout le catalogue pour une valeur que personne ne lit.
        whenever(getVodStreamsUseCase("all")).thenReturn(
            flowOf(listOf(movie(1, "8.0", 2000, "Action, Drame")))
        )
        viewModel = createViewModelWithCategories()
        runCurrent()

        val state = viewModel.state.value
        assertEquals("all", state.selectedCategory?.categoryId)
        assertTrue(state.streams.isNotEmpty())
        assertTrue(state.availableGenres.isEmpty())
        assertEquals(0, state.filteredCount)
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

        // Une plage qui couvre toute la catégorie ne filtre rien : normalisée à
        // null comme dans FavoritesViewModel, sinon un chip « actif » resterait
        // affiché sans effet sur la grille.
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

        // Le seul film « Comédie » est noté 3.0 : les critères se cumulent.
        viewModel.toggleGenre("Comédie")
        assertEquals(0, viewModel.state.value.filteredCount)

        viewModel.resetFilter()
        assertTrue(viewModel.state.value.advancedFilter.isEmpty)
        assertEquals(2, viewModel.state.value.filteredCount)
    }

    // --- F39 §8.6, tâche 5c : versions candidates d'un film ---

    @Test
    fun `getMovieVersions returns an empty list when the movie no longer exists in cache`() = runTest(testDispatcher) {
        whenever(vodRepository.observeAllPlaybackPositions()).thenReturn(flowOf(emptyList()))
        whenever(vodRepository.getStreamById(42)).thenReturn(null)
        val viewModel = createViewModel()

        assertTrue(viewModel.getMovieVersions(42).isEmpty())
    }

    @Test
    fun `getMovieVersions returns an empty list when linkKey is not yet normalized`() = runTest(testDispatcher) {
        whenever(vodRepository.observeAllPlaybackPositions()).thenReturn(flowOf(emptyList()))
        whenever(vodRepository.getStreamById(42)).thenReturn(vodStream(42, linkKey = ""))
        val viewModel = createViewModel()

        assertTrue(viewModel.getMovieVersions(42).isEmpty())
    }

    @Test
    fun `getMovieVersions queries by linkKey and release year, including the current entry`() = runTest(testDispatcher) {
        whenever(vodRepository.observeAllPlaybackPositions()).thenReturn(flowOf(emptyList()))
        val current = vodStream(42, linkKey = "key-a", releaseYear = 2020)
        val versions = listOf(current, vodStream(43, linkKey = "key-a", releaseYear = 2020))
        whenever(vodRepository.getStreamById(42)).thenReturn(current)
        whenever(vodRepository.getVersionsByLinkKey("key-a", 2020)).thenReturn(versions)
        val viewModel = createViewModel()

        val result = viewModel.getMovieVersions(42)

        assertEquals(versions, result)
        assertTrue(result.any { it.streamId == 42 })
    }

    private fun vodStream(streamId: Int, linkKey: String, releaseYear: Int? = null) = VodStream(
        streamId = streamId, name = "Film $streamId", streamIcon = null, rating = null, added = null,
        categoryId = "1", releaseYear = releaseYear, linkKey = linkKey
    )

    private fun createViewModel() = VodViewModel(
        getVodCategoriesUseCase, getVodCategoryCountsUseCase, getVodStreamsUseCase,
        getVodDetailsUseCase, getRelatedMoviesUseCase, savePlaybackPositionUseCase, credentialsManager,
        settingsManager, trackPreferenceRepository, categoryPreferenceRepository, vodRepository,
        removeFromContinueWatchingUseCase, mediaRatingRepository, setMediaRatingUseCase,
        observeCatalogStatusUseCase, catalogSyncManager, canPlayContentUseCase, getTrailerPreviewUseCase, invalidateTrailerPreviewUseCase,
        getRecommendationsUseCase, testDispatcher
    )
}
