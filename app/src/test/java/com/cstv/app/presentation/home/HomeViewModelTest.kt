package com.cstv.app.presentation.home

import com.cstv.app.domain.model.*
import com.cstv.app.domain.repository.FavoritesRepository
import com.cstv.app.domain.repository.DownloadRepository
import com.cstv.app.domain.repository.LiveTvRepository
import com.cstv.app.domain.repository.SeriesRepository
import com.cstv.app.domain.repository.VodRepository
import com.cstv.app.data.local.storage.ProfileManager
import com.cstv.app.presentation.components.TrailerPreviewUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import androidx.lifecycle.viewModelScope
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
class HomeViewModelTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (ticker infini dans un `init` de ViewModel, `advanceUntilIdle` sur une
    // tâche périodique) fige le build sans jamais échouer. Cette règle nomme le
    // test fautif ; le garde-fou dur est `tasks.withType<Test> { timeout }`
    // dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)


    @Mock
    private lateinit var vodRepository: VodRepository

    @Mock
    private lateinit var liveTvRepository: LiveTvRepository

    @Mock
    private lateinit var seriesRepository: SeriesRepository

    @Mock
    private lateinit var favoritesRepository: FavoritesRepository

    @Mock
    private lateinit var downloadRepository: DownloadRepository

    @Mock
    private lateinit var getLiveEpgUseCase: com.cstv.app.domain.usecase.GetLiveEpgUseCase

    @Mock
    private lateinit var getLiveCategoriesUseCase: com.cstv.app.domain.usecase.GetLiveCategoriesUseCase

    @Mock
    private lateinit var getVodCategoriesUseCase: com.cstv.app.domain.usecase.GetVodCategoriesUseCase

    @Mock
    private lateinit var getSeriesCategoriesUseCase: com.cstv.app.domain.usecase.GetSeriesCategoriesUseCase

    @Mock
    private lateinit var canPlayContentUseCase: com.cstv.app.domain.usecase.CanPlayContentUseCase

    @Mock
    private lateinit var categoryPreferenceRepository: com.cstv.app.domain.repository.CategoryPreferenceRepository

    @Mock
    private lateinit var getTrendingInCatalogUseCase: com.cstv.app.domain.usecase.GetTrendingInCatalogUseCase

    @Mock
    private lateinit var getRecommendationsUseCase: com.cstv.app.domain.usecase.GetRecommendationsUseCase

    @Mock
    private lateinit var getPopularTop10InCatalogUseCase: com.cstv.app.domain.usecase.GetPopularTop10InCatalogUseCase

    @Mock
    private lateinit var removeFromContinueWatchingUseCase: com.cstv.app.domain.usecase.RemoveFromContinueWatchingUseCase

    @Mock
    private lateinit var getTrailerPreviewUseCase: com.cstv.app.domain.usecase.GetTrailerPreviewUseCase

    @Mock
    private lateinit var invalidateTrailerPreviewUseCase: com.cstv.app.domain.usecase.InvalidateTrailerPreviewUseCase

    @Mock
    private lateinit var profileManager: ProfileManager

    // Phase 42 : StandardTestDispatcher (et non Unconfined), pour garder la
    // maîtrise de l'instant virtuel. Le ticker EPG de `HomeViewModel.init` ne
    // planifie plus de `delay` tant que personne ne collecte `state` : lire
    // `viewModel.state.value` n'ouvre pas d'abonnement, donc `advanceUntilIdle()`
    // et le nettoyage de `runTest` terminent normalement (voir le test de
    // non-régression `epgTicker_*` en fin de fichier).
    private val testDispatcher = StandardTestDispatcher()
    private val activeProfileId = MutableStateFlow(1)

    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        activeProfileId.value = 1
        doReturn(activeProfileId).whenever(profileManager).activeProfileId
    }

    @After
    fun tearDown() {
        if (::viewModel.isInitialized) {
            viewModel.viewModelScope.cancel()
        }
        Dispatchers.resetMain()
    }

    private fun createViewModel(): HomeViewModel {
        val vm = HomeViewModel(
            vodRepository,
            liveTvRepository,
            seriesRepository,
            favoritesRepository,
            downloadRepository,
            getLiveEpgUseCase,
            getLiveCategoriesUseCase,
            getVodCategoriesUseCase,
            getSeriesCategoriesUseCase,
            categoryPreferenceRepository,
            getTrendingInCatalogUseCase,
            getRecommendationsUseCase,
            getPopularTop10InCatalogUseCase,
            removeFromContinueWatchingUseCase,
            getTrailerPreviewUseCase,
            invalidateTrailerPreviewUseCase,
            profileManager,
            canPlayContentUseCase
        )
        testDispatcher.scheduler.runCurrent()
        return vm
    }

    // Phase 41 : resume/favorites viennent maintenant de Flows continus
    // (observeAllPlaybackPositions/observeFavorites) plutôt que d'un fetch
    // ponctuel dans loadHomeData ; ce helper les stub par défaut à vide.
    private suspend fun stubReactiveSources(
        positions: List<PlaybackPosition> = emptyList(),
        favorites: List<FavoriteItem> = emptyList(),
        cachedPopularMovies: List<VodStream>? = null,
        cachedPopularSeries: List<SeriesStream>? = null,
        freshPopularMovies: List<VodStream>? = null,
        freshPopularSeries: List<SeriesStream>? = null,
        // T8-R1 : par défaut périmé, pour préserver le comportement des tests
        // existants qui attendent une actualisation silencieuse dès qu'un
        // cache est affiché ; les tests dédiés au cas "cache frais" le
        // désactivent explicitement.
        moviesCacheExpired: Boolean = true,
        seriesCacheExpired: Boolean = true
    ) {
        doReturn(flowOf(positions)).whenever(vodRepository).observeAllPlaybackPositions()
        // Cas nominal : les catalogues existent ; une catégorie inconnue est
        // donc masquée. Les tests du démarrage à froid remplacent ces valeurs.
        whenever(vodRepository.hasCachedVodStreams()).thenReturn(true)
        whenever(seriesRepository.hasCachedSeriesStreams()).thenReturn(true)
        doReturn(flowOf(favorites)).whenever(favoritesRepository).observeFavorites()
        doReturn(flowOf(emptyList<DownloadedItem>())).whenever(downloadRepository).observeDownloads()
        // Phase 58 : flux de changements de préférences collecté dans init{},
        // et préférences vides par défaut (aucune catégorie masquée).
        doReturn(flowOf(Unit)).whenever(categoryPreferenceRepository).changes
        doReturn(kotlinx.coroutines.flow.MutableSharedFlow<Unit>()).whenever(getRecommendationsUseCase).invalidations
        // Les tendances sont lues en deux temps (cache immédiat puis
        // rafraîchissement) : sans stub de `cached()`, l'appel suspendu du mock
        // ne reprend jamais et le test se bloque.
        whenever(getTrendingInCatalogUseCase.isCacheExpired()).thenReturn(false)
        whenever(getTrendingInCatalogUseCase.cached()).thenReturn(emptyList())
        whenever(getTrendingInCatalogUseCase.invoke()).thenReturn(emptyList())
        whenever(getRecommendationsUseCase.invoke(any())).thenReturn(
            com.cstv.app.domain.usecase.GetRecommendationsUseCase.RecommendationResult(emptyList(), emptyList())
        )
        whenever(getVodCategoriesUseCase.once()).thenReturn(emptyList())
        whenever(getSeriesCategoriesUseCase.once()).thenReturn(emptyList())
        // T8 : le cache (quel que soit son âge) prime sur le réseau ; `null`
        // simule l'absence de cache, qui déclenche le chargement à froid.
        whenever(getPopularTop10InCatalogUseCase.cachedMovies()).thenReturn(cachedPopularMovies)
        whenever(getPopularTop10InCatalogUseCase.cachedSeries()).thenReturn(cachedPopularSeries)
        whenever(getPopularTop10InCatalogUseCase.loadFreshMovies()).thenReturn(freshPopularMovies)
        whenever(getPopularTop10InCatalogUseCase.loadFreshSeries()).thenReturn(freshPopularSeries)
        whenever(getPopularTop10InCatalogUseCase.isMoviesCacheExpired()).thenReturn(moviesCacheExpired)
        whenever(getPopularTop10InCatalogUseCase.isSeriesCacheExpired()).thenReturn(seriesCacheExpired)
    }

    private suspend fun stubEmptyCategoryPreferences() {
        whenever(categoryPreferenceRepository.getPreferences(any())).thenReturn(emptyMap())
    }

    private fun downloadedItem(
        id: Int,
        status: DownloadStatus = DownloadStatus.COMPLETED,
        percent: Int = 100
    ) = DownloadedItem(
        contentId = "movie_$id",
        type = DownloadedItem.TYPE_MOVIE,
        streamId = id,
        seriesId = null,
        seasonNum = null,
        episodeNum = null,
        title = "Téléchargement $id",
        subtitle = null,
        coverUrl = null,
        containerExtension = "mp4",
        status = status,
        percent = percent,
        bytesDownloaded = 0L,
        totalBytes = 0L
    )

    @Test
    fun downloadsOnlyExposeCompletedItemsInRepositoryOrderAndLimitToTwenty() = runTest {
        stubReactiveSources()
        stubEmptyCategoryPreferences()
        val downloads = MutableStateFlow(
            listOf(downloadedItem(99, DownloadStatus.DOWNLOADING, 50)) + (1..21).map(::downloadedItem)
        )
        doReturn(downloads).whenever(downloadRepository).observeDownloads()

        viewModel = createViewModel()

        assertEquals((1..20).toList(), viewModel.state.value.downloadedItems.map { it.streamId })
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun downloadsEmptyWhenNoCompletedItemExistsAndUpdatesAfterRemoval() = runTest {
        stubReactiveSources()
        stubEmptyCategoryPreferences()
        val first = downloadedItem(1)
        val downloads = MutableStateFlow(listOf(downloadedItem(2, DownloadStatus.FAILED)))
        doReturn(downloads).whenever(downloadRepository).observeDownloads()

        viewModel = createViewModel()
        assertTrue(viewModel.state.value.downloadedItems.isEmpty())

        downloads.value = listOf(first)
        testDispatcher.scheduler.runCurrent()
        assertEquals(listOf(first), viewModel.state.value.downloadedItems)

        downloads.value = emptyList()
        testDispatcher.scheduler.runCurrent()
        assertTrue(viewModel.state.value.downloadedItems.isEmpty())
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun downloadingProgressUpdatesDoNotChangeCompletedDownloadsState() = runTest {
        stubReactiveSources()
        stubEmptyCategoryPreferences()
        val completed = downloadedItem(1)
        val downloads = MutableStateFlow(listOf(completed, downloadedItem(2, DownloadStatus.DOWNLOADING, 20)))
        doReturn(downloads).whenever(downloadRepository).observeDownloads()

        viewModel = createViewModel()
        val stateBeforeProgress = viewModel.state.value
        downloads.value = listOf(completed, downloadedItem(2, DownloadStatus.DOWNLOADING, 80))
        testDispatcher.scheduler.runCurrent()

        assertSame(stateBeforeProgress, viewModel.state.value)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun profileChangeReloadsHomeContent() = runTest {
        stubReactiveSources()
        stubEmptyCategoryPreferences()
        whenever(getLiveCategoriesUseCase.once()).thenReturn(emptyList())

        viewModel = createViewModel()
        activeProfileId.value = 2
        testDispatcher.scheduler.runCurrent()

        verify(getLiveCategoriesUseCase, times(2)).once()

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun test_loadHomeData_success_populatesAllSections() = runTest {
        val positions = listOf(
            // Phase 58 : une reprise sans categoryId est masquée dès que le
            // catalogue correspondant est peuplé (cas nominal de
            // `stubReactiveSources`) — la position doit donc porter sa catégorie.
            PlaybackPosition(1, 1000L, 50000L, System.currentTimeMillis(), "Movie 1", "cover1", "movie", "mp4", categoryId = "1")
        )
        val favorites = listOf(
            FavoriteItem(2, "live", "Live 1", "cover2", "cat1")
        )
        stubReactiveSources(positions, favorites)
        stubEmptyCategoryPreferences()

        // Mock Live TV
        val liveCats = listOf(LiveCategory("1", "Live Cat 1", 0))
        val liveStreams = listOf(LiveStream(101, "Channel 1", "icon1", null, 1, "1"))
        whenever(getLiveCategoriesUseCase.once()).thenReturn(liveCats)
        whenever(liveTvRepository.getCachedLiveStreams("1")).thenReturn(liveStreams)

        // Mock VOD Movies
        val vodCategories = listOf(VodCategory("1", "VOD Cat 1", 0))
        val vodStreams = listOf(VodStream(201, "Movie A", "icon2", "8.5", "2026", "1"))
        whenever(getVodCategoriesUseCase.once()).thenReturn(vodCategories)
        whenever(vodRepository.getCachedVodStreams("1")).thenReturn(vodStreams)

        // Mock Series
        val seriesCategories = listOf(SeriesCategory("1", "Series Cat 1", 0))
        val seriesStreams = listOf(SeriesStream(301, "Series X", "cover3", "9.0", "2026", "1"))
        whenever(getSeriesCategoriesUseCase.once()).thenReturn(seriesCategories)
        whenever(seriesRepository.getCachedSeriesStreams("1")).thenReturn(seriesStreams)

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(1, state.resumeWatchingList.size)
        assertEquals("Movie 1", state.resumeWatchingList[0].title)
        assertEquals(1, state.favoritesList.size)
        assertEquals("Live 1", state.favoritesList[0].name)
        assertEquals("1", state.firstLiveCategory?.categoryId)
        assertEquals(1, state.firstLiveStreams.size)
        assertEquals("Channel 1", state.firstLiveStreams[0].name)
        assertEquals(1, state.firstVodStreams.size)
        assertEquals("Movie A", state.firstVodStreams[0].name)
        assertEquals(1, state.firstSeriesStreams.size)
        assertEquals("Series X", state.firstSeriesStreams[0].name)

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun test_loadHomeData_popularRowsHaveNoCatalogFallback() = runTest {
        val popularMovie = VodStream(999, "Popular movie", null, null, null, "movies")
        stubReactiveSources(freshPopularMovies = listOf(popularMovie))
        stubEmptyCategoryPreferences()
        whenever(getLiveCategoriesUseCase.once()).thenReturn(emptyList())

        viewModel = createViewModel()

        assertEquals(listOf(popularMovie), viewModel.state.value.popularTopVodStreams)
        assertNull(viewModel.state.value.popularTopSeriesStreams)
        verify(vodRepository, never()).getCachedVodStreams("all")
        verify(seriesRepository, never()).getCachedSeriesStreams("all")

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun test_loadHomeData_usesFirstVisibleCategoriesWithoutGlobalCatalogReads() = runTest {
        stubReactiveSources()
        stubEmptyCategoryPreferences()
        whenever(getLiveCategoriesUseCase.once()).thenReturn(emptyList())
        whenever(getVodCategoriesUseCase.once()).thenReturn(
            listOf(VodCategory("action", "Action", 0), VodCategory("comedy", "Comédie", 0))
        )
        whenever(getSeriesCategoriesUseCase.once()).thenReturn(
            listOf(SeriesCategory("drama", "Drame", 0))
        )
        whenever(vodRepository.getCachedVodStreams("action")).thenReturn(
            (1..21).map { VodStream(it, "Film $it", null, null, null, "action") }
        )
        whenever(seriesRepository.getCachedSeriesStreams("drama")).thenReturn(
            (1..21).map { SeriesStream(it, "Série $it", null, null, null, "drama") }
        )

        viewModel = createViewModel()

        assertEquals("action", viewModel.state.value.firstVodCategory?.categoryId)
        assertEquals(20, viewModel.state.value.firstVodStreams.size)
        assertEquals("drama", viewModel.state.value.firstSeriesCategory?.categoryId)
        assertEquals((1..20).toList(), viewModel.state.value.firstSeriesStreams.map { it.seriesId })
        verify(vodRepository, never()).getCachedVodStreams("all")
        verify(seriesRepository, never()).getCachedSeriesStreams("all")
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun loadHomeData_noVisibleCategoriesLeavesRowsAbsent() = runTest {
        stubReactiveSources()
        stubEmptyCategoryPreferences()
        whenever(getLiveCategoriesUseCase.once()).thenReturn(emptyList())

        viewModel = createViewModel()

        assertNull(viewModel.state.value.firstVodCategory)
        assertTrue(viewModel.state.value.firstVodStreams.isEmpty())
        assertNull(viewModel.state.value.firstSeriesCategory)
        assertTrue(viewModel.state.value.firstSeriesStreams.isEmpty())
    }

    @Test
    fun loadHomeData_categoryReadFailureLeavesOnlyThatRowAbsentSilently() = runTest {
        stubReactiveSources()
        stubEmptyCategoryPreferences()
        whenever(getLiveCategoriesUseCase.once()).thenReturn(emptyList())
        whenever(getVodCategoriesUseCase.once()).thenReturn(listOf(VodCategory("action", "Action", 0)))
        whenever(vodRepository.getCachedVodStreams("action")).thenThrow(IllegalStateException("Room"))

        viewModel = createViewModel()

        assertTrue(viewModel.state.value.firstVodStreams.isEmpty())
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun resumeWatching_unknownCategoryIsHiddenWhenItsCatalogExists() = runTest {
        stubReactiveSources(positions = listOf(PlaybackPosition(1, 1_000L, 50_000L, 1L)))
        stubEmptyCategoryPreferences()

        viewModel = createViewModel()

        assertTrue(viewModel.state.value.resumeWatchingList.isEmpty())
        verify(vodRepository, never()).getCachedVodStreams("all")
        verify(seriesRepository, never()).getCachedSeriesStreams("all")
    }

    @Test
    fun resumeWatching_unknownCategoryIsKeptOnlyWhenItsOwnCatalogIsEmpty() = runTest {
        stubReactiveSources(positions = listOf(PlaybackPosition(1, 1_000L, 50_000L, 1L)))
        stubEmptyCategoryPreferences()
        whenever(vodRepository.hasCachedVodStreams()).thenReturn(false)
        whenever(seriesRepository.hasCachedSeriesStreams()).thenReturn(true)

        viewModel = createViewModel()

        assertEquals(listOf(1), viewModel.state.value.resumeWatchingList.map { it.streamId })
    }

    @Test
    fun resumeWatching_unknownSeriesCategoryIsKeptWhenOnlySeriesCatalogIsEmpty() = runTest {
        stubReactiveSources(positions = listOf(PlaybackPosition(2, 1_000L, 50_000L, 1L, seriesId = 9)))
        stubEmptyCategoryPreferences()
        whenever(vodRepository.hasCachedVodStreams()).thenReturn(true)
        whenever(seriesRepository.hasCachedSeriesStreams()).thenReturn(false)

        viewModel = createViewModel()

        assertEquals(listOf(2), viewModel.state.value.resumeWatchingList.map { it.streamId })
    }

    // --- T8 (Tâche 3) : rafraîchissement silencieux du cache Popular au redémarrage ---

    @Test
    fun test_popularCache_preexistingCacheIsDisplayedImmediatelyRegardlessOfAge() = runTest {
        val cachedMovie = VodStream(1, "Cached movie", null, null, null, "movies")
        val cachedSeriesItem = SeriesStream(2, "Cached series", null, null, null, "series")
        stubReactiveSources(cachedPopularMovies = listOf(cachedMovie), cachedPopularSeries = listOf(cachedSeriesItem))
        stubEmptyCategoryPreferences()
        whenever(getLiveCategoriesUseCase.once()).thenReturn(emptyList())

        viewModel = createViewModel()

        assertEquals(listOf(cachedMovie), viewModel.state.value.popularTopVodStreams)
        assertEquals(listOf(cachedSeriesItem), viewModel.state.value.popularTopSeriesStreams)
        // Le cache prime : le réseau "à froid" n'a pas dû être sollicité.
        verify(getPopularTop10InCatalogUseCase, never()).loadFreshMovies()
        verify(getPopularTop10InCatalogUseCase, never()).loadFreshSeries()

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun test_popularCache_silentBackgroundRefreshRunsButNeverReplacesTheDisplayedSessionState() = runTest {
        val cachedMovie = VodStream(1, "Cached movie", null, null, null, "movies")
        stubReactiveSources(cachedPopularMovies = listOf(cachedMovie))
        stubEmptyCategoryPreferences()
        whenever(getLiveCategoriesUseCase.once()).thenReturn(emptyList())
        whenever(vodRepository.getCachedVodStreams("all")).thenReturn(emptyList())
        whenever(seriesRepository.getCachedSeriesStreams("all")).thenReturn(emptyList())

        // `runCurrent()` suffit : seul le chargement initial est attendu ici.
        viewModel = createViewModel()
        testDispatcher.scheduler.runCurrent()

        // L'actualisation silencieuse a bien eu lieu (persistance)...
        verify(getPopularTop10InCatalogUseCase).refreshMoviesSilently()
        // ...mais l'état affiché reste celui du cache initial de la session.
        assertEquals(listOf(cachedMovie), viewModel.state.value.popularTopVodStreams)

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun test_popularCache_coldStartWithoutCacheUpdatesUiFromNetworkResult() = runTest {
        val freshMovie = VodStream(1, "Fresh movie", null, null, null, "movies")
        stubReactiveSources(freshPopularMovies = listOf(freshMovie))
        stubEmptyCategoryPreferences()
        whenever(getLiveCategoriesUseCase.once()).thenReturn(emptyList())
        whenever(vodRepository.getCachedVodStreams("all")).thenReturn(emptyList())
        whenever(seriesRepository.getCachedSeriesStreams("all")).thenReturn(emptyList())

        viewModel = createViewModel()

        assertEquals(listOf(freshMovie), viewModel.state.value.popularTopVodStreams)
        verify(getPopularTop10InCatalogUseCase, never()).refreshMoviesSilently()

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun test_popularCache_missingCacheForOneRowDoesNotBlockOrHideTheOtherRow() = runTest {
        val cachedMovie = VodStream(1, "Cached movie", null, null, null, "movies")
        val freshSeriesItem = SeriesStream(2, "Fresh series", null, null, null, "series")
        // Films en cache, séries sans cache (règle 7 : indépendance des deux rangées).
        stubReactiveSources(cachedPopularMovies = listOf(cachedMovie), freshPopularSeries = listOf(freshSeriesItem))
        stubEmptyCategoryPreferences()
        whenever(getLiveCategoriesUseCase.once()).thenReturn(emptyList())
        whenever(vodRepository.getCachedVodStreams("all")).thenReturn(emptyList())
        whenever(seriesRepository.getCachedSeriesStreams("all")).thenReturn(emptyList())

        viewModel = createViewModel()

        assertEquals(listOf(cachedMovie), viewModel.state.value.popularTopVodStreams)
        assertEquals(listOf(freshSeriesItem), viewModel.state.value.popularTopSeriesStreams)

        viewModel.viewModelScope.cancel()
    }

    // T8-R1 : un cache encore frais (< 24h, cohérent avec le catalogue actuel)
    // ne doit générer aucune actualisation réseau silencieuse.
    @Test
    fun test_popularCache_freshCacheDoesNotTriggerSilentRefresh() = runTest {
        val cachedMovie = VodStream(1, "Cached movie", null, null, null, "movies")
        val cachedSeriesItem = SeriesStream(2, "Cached series", null, null, null, "series")
        stubReactiveSources(
            cachedPopularMovies = listOf(cachedMovie),
            cachedPopularSeries = listOf(cachedSeriesItem),
            moviesCacheExpired = false,
            seriesCacheExpired = false
        )
        stubEmptyCategoryPreferences()
        whenever(getLiveCategoriesUseCase.once()).thenReturn(emptyList())
        whenever(vodRepository.getCachedVodStreams("all")).thenReturn(emptyList())
        whenever(seriesRepository.getCachedSeriesStreams("all")).thenReturn(emptyList())

        viewModel = createViewModel()
        testDispatcher.scheduler.runCurrent()

        assertEquals(listOf(cachedMovie), viewModel.state.value.popularTopVodStreams)
        assertEquals(listOf(cachedSeriesItem), viewModel.state.value.popularTopSeriesStreams)
        verify(getPopularTop10InCatalogUseCase, never()).refreshMoviesSilently()
        verify(getPopularTop10InCatalogUseCase, never()).refreshSeriesSilently()

        viewModel.viewModelScope.cancel()
    }

    // T8-R2 / T8-R3 : un rechargement de la Home dans la même session (ex :
    // changement de préférences de catégories) ne doit ni relire, ni
    // republier, ni relancer une actualisation silencieuse pour une rangée
    // déjà figée par un précédent chargement.
    @Test
    fun test_popularCache_reloadInSameSessionDoesNotReplayAnAlreadyResolvedRow() = runTest {
        val cachedMovie = VodStream(1, "Cached movie", null, null, null, "movies")
        stubReactiveSources(cachedPopularMovies = listOf(cachedMovie))
        stubEmptyCategoryPreferences()
        whenever(getLiveCategoriesUseCase.once()).thenReturn(emptyList())
        whenever(vodRepository.getCachedVodStreams("all")).thenReturn(emptyList())
        whenever(seriesRepository.getCachedSeriesStreams("all")).thenReturn(emptyList())

        viewModel = createViewModel()
        testDispatcher.scheduler.runCurrent()
        assertEquals(listOf(cachedMovie), viewModel.state.value.popularTopVodStreams)

        viewModel.loadHomeData()
        testDispatcher.scheduler.runCurrent()

        assertEquals(listOf(cachedMovie), viewModel.state.value.popularTopVodStreams)
        verify(getPopularTop10InCatalogUseCase, times(1)).cachedMovies()
        verify(getPopularTop10InCatalogUseCase, times(1)).refreshMoviesSilently()

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun test_loadHomeData_partialFailure_keepsOtherSectionsFunctional() = runTest {
        stubReactiveSources()
        stubEmptyCategoryPreferences()

        // Mock Live TV throws exception
        whenever(getLiveCategoriesUseCase.once()).thenThrow(RuntimeException("API Error Live TV"))

        // Mock VOD Movies succeeds — la rangée Films provient de la première
        // catégorie visible du profil (T10), pas d'un pseudo-identifiant "all".
        val vodStreams = listOf(VodStream(201, "Movie A", "icon2", "8.5", "2026", "1"))
        whenever(getVodCategoriesUseCase.once()).thenReturn(listOf(VodCategory("1", "VOD Cat 1", 0)))
        whenever(vodRepository.getCachedVodStreams("1")).thenReturn(vodStreams)

        // Mock Series to be empty
        whenever(seriesRepository.getCachedSeriesStreams("all")).thenReturn(emptyList())

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNull(state.error) // Should not fail completely
        assertTrue(state.resumeWatchingList.isEmpty())
        assertTrue(state.favoritesList.isEmpty())
        assertNull(state.firstLiveCategory)
        assertTrue(state.firstLiveStreams.isEmpty())

        // VOD should be loaded successfully
        assertEquals(1, state.firstVodStreams.size)
        assertEquals("Movie A", state.firstVodStreams[0].name)

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun test_loadHomeData_resumeWatching_groupsSeriesEpisodesBySeriesId_keepingMostRecentOnly() = runTest {
        // observeAllPlaybackPositions trie déjà par lastAccessedAt DESC : le
        // repository (mocké ici) doit refléter cet ordre pour que le
        // regroupement (Phase 30) retienne bien le dernier épisode vu.
        // `categoryId` renseigné partout : sans lui, Phase 58 masque la reprise
        // dès que le catalogue correspondant est peuplé, ce qui n'est pas le
        // point testé ici (regroupement par seriesId).
        val positions = listOf(
            // Série 10 : épisode le plus récent (doit être conservé)
            PlaybackPosition(102, 1000L, 50000L, 3000L, "Show A - S1E2", "cover", "series", "mp4", seriesId = 10, episodeNum = 2, seasonNum = 1, categoryId = "1"),
            // Film sans seriesId : jamais regroupé
            PlaybackPosition(501, 1000L, 50000L, 2500L, "Movie 1", "cover1", "movie", "mp4", categoryId = "1"),
            // Série 10 : épisode plus ancien (doit être exclu, même série que ci-dessus)
            PlaybackPosition(101, 1000L, 50000L, 2000L, "Show A - S1E1", "cover", "series", "mp4", seriesId = 10, episodeNum = 1, seasonNum = 1, categoryId = "1"),
            // Série 20 : une seule entrée, forcément conservée
            PlaybackPosition(201, 1000L, 50000L, 1000L, "Show B - S1E1", "cover", "series", "mp4", seriesId = 20, episodeNum = 1, seasonNum = 1, categoryId = "1")
        )
        stubReactiveSources(positions = positions)
        stubEmptyCategoryPreferences()
        whenever(getLiveCategoriesUseCase.once()).thenReturn(emptyList())
        whenever(vodRepository.getCachedVodStreams("all")).thenReturn(emptyList())
        whenever(seriesRepository.getCachedSeriesStreams("all")).thenReturn(emptyList())

        viewModel = createViewModel()
        advanceUntilIdle()

        val resumeWatching = viewModel.state.value.resumeWatchingList
        assertEquals(3, resumeWatching.size)
        assertTrue(resumeWatching.any { it.streamId == 102 }) // dernier épisode de la série 10
        assertFalse(resumeWatching.any { it.streamId == 101 }) // ancien épisode de la série 10, exclu
        assertTrue(resumeWatching.any { it.streamId == 501 }) // film, toujours présent
        assertTrue(resumeWatching.any { it.streamId == 201 }) // seule entrée de la série 20

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun test_loadHomeData_limitsFirstVodAndSeriesRowsTo20_keepingRepositoryOrder() = runTest {
        stubReactiveSources()
        stubEmptyCategoryPreferences()
        whenever(getLiveCategoriesUseCase.once()).thenReturn(emptyList())

        // Le tri par date d'ajout décroissante est fait par le DAO (T9) : le
        // repository est donc mocké déjà ordonné. Le ViewModel ne doit que
        // tronquer à HOME_ROW_LIMIT (20) en préservant cet ordre.
        val vodStreams = (21 downTo 0).map { i -> VodStream(i, "Movie $i", "icon", "5.0", i.toString(), "1") }
        val seriesStreams = (21 downTo 0).map { i -> SeriesStream(i, "Series $i", "cover", "5.0", i.toString(), "1") }
        whenever(getVodCategoriesUseCase.once()).thenReturn(listOf(VodCategory("1", "VOD Cat 1", 0)))
        whenever(getSeriesCategoriesUseCase.once()).thenReturn(listOf(SeriesCategory("1", "Series Cat 1", 0)))
        whenever(vodRepository.getCachedVodStreams("1")).thenReturn(vodStreams)
        whenever(seriesRepository.getCachedSeriesStreams("1")).thenReturn(seriesStreams)

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(20, state.firstVodStreams.size)
        assertEquals("Movie 21", state.firstVodStreams.first().name)
        assertEquals("Movie 2", state.firstVodStreams.last().name)

        assertEquals(20, state.firstSeriesStreams.size)
        assertEquals("Series 21", state.firstSeriesStreams.first().name)
        assertEquals("Series 2", state.firstSeriesStreams.last().name)

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun test_loadHomeData_liveTvFallback_keepsFirstCategoryLogic_whenSucceeds() = runTest {
        stubReactiveSources()
        stubEmptyCategoryPreferences()
        val liveCats = listOf(LiveCategory("1", "Live Cat 1", 0), LiveCategory("2", "Live Cat 2", 1))
        val liveStreams = listOf(LiveStream(101, "Channel 1", "icon1", null, 1, "1"))
        whenever(getLiveCategoriesUseCase.once()).thenReturn(liveCats)
        whenever(liveTvRepository.getCachedLiveStreams("1")).thenReturn(liveStreams)
        whenever(vodRepository.getCachedVodStreams("all")).thenReturn(emptyList())
        whenever(seriesRepository.getCachedSeriesStreams("all")).thenReturn(emptyList())

        viewModel = createViewModel()

        val state = viewModel.state.value
        // TV en direct garde la logique "première catégorie" (pas de notion d'ajout exploitable)
        assertEquals("1", state.firstLiveCategory?.categoryId)
        assertEquals(1, state.firstLiveStreams.size)

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun test_resumeWatchingAndFavorites_filtersHiddenCategories() = runTest {
        val positions = listOf(
            PlaybackPosition(101, 1000L, 50000L, System.currentTimeMillis(), "Movie 101", "cover1", "movie", "mp4", seriesId = null, categoryId = "hidden_vod_cat"),
            PlaybackPosition(201, 1000L, 50000L, System.currentTimeMillis(), "Episode 201", "cover2", "series", "mp4", seriesId = 1001, categoryId = "visible_series_cat")
        )
        val favorites = listOf(
            FavoriteItem(101, "movie", "Movie 101", "cover1", "hidden_vod_cat"),
            FavoriteItem(201, "series", "Series 201", "cover2", "visible_series_cat")
        )

        stubReactiveSources(positions, favorites)

        // Mock category preferences: "hidden_vod_cat" is hidden
        whenever(categoryPreferenceRepository.getPreferences(CategoryType.VOD)).thenReturn(
            mapOf("hidden_vod_cat" to CategoryPreference("hidden_vod_cat", hidden = true, sortOrder = null))
        )
        whenever(categoryPreferenceRepository.getPreferences(CategoryType.SERIES)).thenReturn(emptyMap())
        whenever(categoryPreferenceRepository.getPreferences(CategoryType.LIVE)).thenReturn(emptyMap())


        viewModel = createViewModel()

        val state = viewModel.state.value

        // Favorites: "hidden_vod_cat" is hidden, so "Movie 101" should be filtered out. Only Series 201 should remain.
        assertEquals(1, state.favoritesList.size)
        assertEquals(201, state.favoritesList[0].id)

        // Resume Watching: Movie 101 is in "hidden_vod_cat", so it must be filtered out. Only Episode 201 should remain.
        assertEquals(1, state.resumeWatchingList.size)
        assertEquals(1001, state.resumeWatchingList[0].seriesId)

        viewModel.viewModelScope.cancel()
    }

    // Les tendances sont publiées en deux temps : cache persistant d'abord (pour
    // que la Hero Card soit là dès le premier rendu), rafraîchissement ensuite.
    // Un rafraîchissement vide (TMDB injoignable) ne doit jamais effacer ce qui
    // est déjà affiché.
    @Test
    fun test_loadHomeData_servesCachedTrendsAndKeepsThemWhenRefreshIsEmpty() = runTest {
        stubReactiveSources()
        stubEmptyCategoryPreferences()

        val cachedTrends = listOf(
            TrendingCatalogItem(
                trendingTitle = TrendingTitle(1, "Dune", isMovie = true, year = 2021, posterUrl = "url_dune"),
                matchedMovie = VodStream(10, "Dune", "icon", "8.0", "12345", "1")
            )
        )
        whenever(getTrendingInCatalogUseCase.cached()).thenReturn(cachedTrends)
        whenever(getTrendingInCatalogUseCase.invoke()).thenReturn(emptyList())
        whenever(getLiveCategoriesUseCase.once()).thenReturn(emptyList())
        whenever(vodRepository.getCachedVodStreams("all")).thenReturn(emptyList())
        whenever(seriesRepository.getCachedSeriesStreams("all")).thenReturn(emptyList())

        // `runCurrent()` suffit : seul le chargement initial est attendu ici.
        viewModel = createViewModel()
        testDispatcher.scheduler.runCurrent()

        val state = viewModel.state.value
        assertEquals(1, state.trendingList.size)
        assertEquals("Dune", state.trendingList[0].trendingTitle.title)
        assertEquals(false, state.awaitingTrending)

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun test_loadHomeData_replacesFreshCachedTrendsWithRefresh() = runTest {
        stubReactiveSources()
        stubEmptyCategoryPreferences()

        val cachedTrend = TrendingCatalogItem(
            trendingTitle = TrendingTitle(1, "Dune", isMovie = true, year = 2021, posterUrl = "url_dune"),
            matchedMovie = VodStream(10, "Dune", "icon", "8.0", "12345", "1")
        )
        val refreshedTrend = TrendingCatalogItem(
            trendingTitle = TrendingTitle(2, "Inception", isMovie = true, year = 2010, posterUrl = "url_inc"),
            matchedMovie = VodStream(11, "Inception", "icon", "9.0", "12345", "1")
        )
        whenever(getTrendingInCatalogUseCase.isCacheExpired()).thenReturn(false)
        whenever(getTrendingInCatalogUseCase.cached()).thenReturn(listOf(cachedTrend))
        whenever(getTrendingInCatalogUseCase.invoke()).thenReturn(listOf(refreshedTrend))
        whenever(getLiveCategoriesUseCase.once()).thenReturn(emptyList())
        whenever(vodRepository.getCachedVodStreams("all")).thenReturn(emptyList())
        whenever(seriesRepository.getCachedSeriesStreams("all")).thenReturn(emptyList())

        viewModel = createViewModel()
        testDispatcher.scheduler.runCurrent()

        assertEquals(listOf(refreshedTrend), viewModel.state.value.trendingList)
        assertFalse(viewModel.state.value.awaitingTrending)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun test_loadHomeData_appendsTrends_whenCacheIsExpired() = runTest {
        stubReactiveSources()
        stubEmptyCategoryPreferences()

        val cachedTrends = listOf(
            TrendingCatalogItem(
                trendingTitle = TrendingTitle(1, "Dune", isMovie = true, year = 2021, posterUrl = "url_dune"),
                matchedMovie = VodStream(10, "Dune", "icon", "8.0", "12345", "1")
            )
        )
        val refreshedTrends = listOf(
            TrendingCatalogItem(
                trendingTitle = TrendingTitle(1, "Dune", isMovie = true, year = 2021, posterUrl = "url_dune"),
                matchedMovie = VodStream(10, "Dune", "icon", "8.0", "12345", "1")
            ),
            TrendingCatalogItem(
                trendingTitle = TrendingTitle(2, "Inception", isMovie = true, year = 2010, posterUrl = "url_inc"),
                matchedMovie = VodStream(11, "Inception", "icon", "9.0", "12345", "1")
            )
        )

        // Cache is expired
        whenever(getTrendingInCatalogUseCase.isCacheExpired()).thenReturn(true)
        whenever(getTrendingInCatalogUseCase.cached()).thenReturn(cachedTrends)
        whenever(getTrendingInCatalogUseCase.invoke()).thenReturn(refreshedTrends)
        whenever(getLiveCategoriesUseCase.once()).thenReturn(emptyList())
        whenever(vodRepository.getCachedVodStreams("all")).thenReturn(emptyList())
        whenever(seriesRepository.getCachedSeriesStreams("all")).thenReturn(emptyList())

        viewModel = createViewModel()
        testDispatcher.scheduler.runCurrent()

        val state = viewModel.state.value
        // Both cached (Dune) and new non-duplicate refreshed (Inception) should be there
        assertEquals(2, state.trendingList.size)
        assertEquals("Dune", state.trendingList[0].trendingTitle.title)
        assertEquals("Inception", state.trendingList[1].trendingTitle.title)
        assertEquals(false, state.awaitingTrending)

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun test_loadHomeData_keepsMovieAndSeriesWithSameTmdbId_whenCacheIsExpired() = runTest {
        stubReactiveSources()
        stubEmptyCategoryPreferences()

        val cachedMovie = TrendingCatalogItem(
            trendingTitle = TrendingTitle(1, "Dune", isMovie = true, year = 2021, posterUrl = "url_dune"),
            matchedMovie = VodStream(10, "Dune", "icon", "8.0", "12345", "1")
        )
        val refreshedSeries = TrendingCatalogItem(
            trendingTitle = TrendingTitle(1, "Dune: Prophecy", isMovie = false, year = 2024, posterUrl = "url_dune_prophecy"),
            matchedSeries = SeriesStream(20, "Dune: Prophecy", "icon", "8.0", "2024", "1")
        )
        whenever(getTrendingInCatalogUseCase.isCacheExpired()).thenReturn(true)
        whenever(getTrendingInCatalogUseCase.cached()).thenReturn(listOf(cachedMovie))
        whenever(getTrendingInCatalogUseCase.invoke()).thenReturn(listOf(refreshedSeries))
        whenever(getLiveCategoriesUseCase.once()).thenReturn(emptyList())
        whenever(vodRepository.getCachedVodStreams("all")).thenReturn(emptyList())
        whenever(seriesRepository.getCachedSeriesStreams("all")).thenReturn(emptyList())

        viewModel = createViewModel()
        testDispatcher.scheduler.runCurrent()

        assertEquals(listOf(cachedMovie, refreshedSeries), viewModel.state.value.trendingList)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun test_loadHomeData_populatesTrendingList() = runTest {
        stubReactiveSources()
        stubEmptyCategoryPreferences()

        val mockTrends = listOf(
            TrendingCatalogItem(
                trendingTitle = TrendingTitle(1, "Inception", isMovie = true, year = 2010, posterUrl = "url_inc"),
                matchedMovie = VodStream(10, "Inception", "icon", "9.0", "12345", "1")
            )
        )
        whenever(getTrendingInCatalogUseCase.invoke()).thenReturn(mockTrends)

        // Stub other methods to succeed with empty list
        whenever(getLiveCategoriesUseCase.once()).thenReturn(emptyList())
        whenever(vodRepository.getCachedVodStreams("all")).thenReturn(emptyList())
        whenever(seriesRepository.getCachedSeriesStreams("all")).thenReturn(emptyList())

        viewModel = createViewModel()

        val state = viewModel.state.value
        assertEquals(1, state.trendingList.size)
        assertEquals("Inception", state.trendingList[0].trendingTitle.title)
        assertEquals(10, state.trendingList[0].matchedMovie!!.streamId)

        viewModel.viewModelScope.cancel()
    }

    // F-6 : les recommandations sont peuplées depuis GetRecommendationsUseCase
    // (découplé de isLoading, comme TMDB) et exposées telles quelles dans le
    // state — c'est ce state qui pilote la visibilité des sections "Recommandé
    // pour vous" (Films/Séries) et leur grille "Voir tout" dans HomeScreen.
    @Test
    fun test_loadHomeData_populatesRecommendedMoviesAndSeries() = runTest {
        stubReactiveSources()
        stubEmptyCategoryPreferences()
        whenever(getLiveCategoriesUseCase.once()).thenReturn(emptyList())
        whenever(vodRepository.getCachedVodStreams("all")).thenReturn(emptyList())
        whenever(seriesRepository.getCachedSeriesStreams("all")).thenReturn(emptyList())

        val recoMovie = VodStream(501, "Recommended Movie", "icon", "8.0", "12345", "1")
        val recoSeries = SeriesStream(601, "Recommended Series", "cover", "8.5", "12345", "1")
        whenever(getRecommendationsUseCase.invoke(any())).thenReturn(
            com.cstv.app.domain.usecase.GetRecommendationsUseCase.RecommendationResult(
                movies = listOf(recoMovie),
                series = listOf(recoSeries)
            )
        )

        viewModel = createViewModel()

        val state = viewModel.state.value
        assertEquals(1, state.recommendedMovies.size)
        assertEquals("Recommended Movie", state.recommendedMovies[0].name)
        assertEquals(1, state.recommendedSeries.size)
        assertEquals("Recommended Series", state.recommendedSeries[0].name)

        viewModel.viewModelScope.cancel()
    }

    // Cold start (F-6) : le use case renvoie des listes vides -> le state reste
    // vide et les sections restent masquées côté HomeScreen (pas de fallback).
    @Test
    fun test_loadHomeData_coldStart_recommendedListsStayEmpty() = runTest {
        stubReactiveSources() // stubReactiveSources() renvoie déjà des listes vides par défaut
        stubEmptyCategoryPreferences()
        whenever(getLiveCategoriesUseCase.once()).thenReturn(emptyList())
        whenever(vodRepository.getCachedVodStreams("all")).thenReturn(emptyList())
        whenever(seriesRepository.getCachedSeriesStreams("all")).thenReturn(emptyList())

        viewModel = createViewModel()

        val state = viewModel.state.value
        assertTrue(state.recommendedMovies.isEmpty())
        assertTrue(state.recommendedSeries.isEmpty())

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun trailerPreview_selectsActiveMediaAndKeepsPosterWhenUnavailable() = runTest {
        stubReactiveSources()
        stubEmptyCategoryPreferences()
        whenever(getLiveCategoriesUseCase.once()).thenReturn(emptyList())
        whenever(vodRepository.getCachedVodStreams("all")).thenReturn(emptyList())
        whenever(seriesRepository.getCachedSeriesStreams("all")).thenReturn(emptyList())
        val item = trendingMovie(10, 100)
        whenever(getTrailerPreviewUseCase.invoke(TrailerMedia.Movie(10, 100))).thenReturn(null)
        viewModel = createViewModel()

        viewModel.selectTrendingPreview(item)
        testDispatcher.scheduler.runCurrent()

        assertEquals(TrailerPreviewUiState.Poster, viewModel.state.value.trailerPreview)
        verify(getTrailerPreviewUseCase).invoke(TrailerMedia.Movie(10, 100))

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun trailerPreview_ignoresCancelledStaleResponseAndResetsForNewMedia() = runTest {
        stubReactiveSources()
        stubEmptyCategoryPreferences()
        whenever(getLiveCategoriesUseCase.once()).thenReturn(emptyList())
        whenever(vodRepository.getCachedVodStreams("all")).thenReturn(emptyList())
        whenever(seriesRepository.getCachedSeriesStreams("all")).thenReturn(emptyList())
        val first = trendingMovie(10, 100)
        val second = trendingMovie(11, 101)
        val firstPreview = TrailerPreview(TrailerMedia.Movie(10, 100), TrailerSource.YouTube("dQw4w9WgXcQ"))
        val secondPreview = TrailerPreview(TrailerMedia.Movie(11, 101), TrailerSource.YouTube("9bZkp7q19f0"))
        whenever(getTrailerPreviewUseCase.invoke(TrailerMedia.Movie(10, 100))).thenReturn(firstPreview)
        whenever(getTrailerPreviewUseCase.invoke(TrailerMedia.Movie(11, 101))).thenReturn(secondPreview)
        viewModel = createViewModel()

        viewModel.selectTrendingPreview(first)
        viewModel.selectTrendingPreview(second)
        testDispatcher.scheduler.runCurrent()

        assertEquals(TrailerPreviewUiState.Playing(secondPreview), viewModel.state.value.trailerPreview)
        assertEquals(TrailerPreviewUiState.Playing(secondPreview), viewModel.state.value.trailerPreview)

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun trailerPreview_playbackFailureOnlyAppliesToCurrentMediaAndCancelsToPoster() = runTest {
        stubReactiveSources()
        stubEmptyCategoryPreferences()
        whenever(getLiveCategoriesUseCase.once()).thenReturn(emptyList())
        whenever(vodRepository.getCachedVodStreams("all")).thenReturn(emptyList())
        whenever(seriesRepository.getCachedSeriesStreams("all")).thenReturn(emptyList())
        val item = trendingMovie(10, 100)
        val media = TrailerMedia.Movie(10, 100)
        val preview = TrailerPreview(media, TrailerSource.YouTube("dQw4w9WgXcQ"))
        whenever(getTrailerPreviewUseCase.invoke(media)).thenReturn(preview)
        viewModel = createViewModel()
        viewModel.selectTrendingPreview(item)
        testDispatcher.scheduler.runCurrent()

        viewModel.reportTrailerPlaybackFailure(TrailerMedia.Movie(99, 999))
        assertEquals(TrailerPreviewUiState.Playing(preview), viewModel.state.value.trailerPreview)
        viewModel.reportTrailerPlaybackFailure(media)
        assertEquals(TrailerPreviewUiState.Failed, viewModel.state.value.trailerPreview)
        viewModel.cancelTrendingPreview()
        assertEquals(TrailerPreviewUiState.Poster, viewModel.state.value.trailerPreview)

        viewModel.viewModelScope.cancel()
    }

    // --- Non-régression : le ticker EPG ne doit jamais figer la suite de tests ---

    // Avant correctif, `HomeViewModel.init` lançait `while (true) { delay(60s) }`
    // sans condition : le scheduler virtuel gardait en permanence une tâche
    // planifiée. `advanceUntilIdle()` — comme le drainage final de `runTest` —
    // bouclait alors pour toujours, figeant `./gradlew testDebugUnitTest` sans
    // aucun échec ni timeout (la boucle de drainage n'est pas suspendable, donc
    // ni interruptible par le timeout de `runTest` ni par une règle JUnit).
    // Ce test échoue par timeout si la régression revient.
    @Test
    fun epgTicker_doesNotKeepTheVirtualSchedulerBusyWhenNobodyObservesState() = runTest {
        stubReactiveSources()
        stubEmptyCategoryPreferences()
        whenever(getLiveCategoriesUseCase.once()).thenReturn(emptyList())
        whenever(vodRepository.getCachedVodStreams("all")).thenReturn(emptyList())
        whenever(seriesRepository.getCachedSeriesStreams("all")).thenReturn(emptyList())

        viewModel = createViewModel()

        // Doit rendre la main : aucun `delay` périodique ne reste planifié.
        advanceUntilIdle()

        viewModel.viewModelScope.cancel()
    }

    private fun trendingMovie(streamId: Int, tmdbId: Int) = TrendingCatalogItem(
        trendingTitle = TrendingTitle("movie:$tmdbId", "Movie $streamId", true, 2026, null),
        matchedMovie = VodStream(streamId, "Movie $streamId", null, null, null, "movies")
    )
}
