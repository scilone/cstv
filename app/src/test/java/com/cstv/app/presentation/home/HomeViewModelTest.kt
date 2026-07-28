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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

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

    // Phase 42 : StandardTestDispatcher (et non Unconfined) + runCurrent() après
    // construction. HomeViewModel lance désormais un ticker EPG infini
    // (while(true) { delay(60s); ... }) dans son init : avec un dispatcher
    // "unconfined" autonome (scheduler non lié à celui de runTest), ce ticker
    // provoque un blocage réel du test. runCurrent() n'exécute que le travail
    // déjà dû à l'instant virtuel courant (le chargement initial), sans jamais
    // avancer jusqu'au premier délai de 60s du ticker.
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
        popular: PopularTop10Result = PopularTop10Result(null, null)
    ) {
        doReturn(flowOf(positions)).whenever(vodRepository).observeAllPlaybackPositions()
        doReturn(flowOf(favorites)).whenever(favoritesRepository).observeFavorites()
        doReturn(flowOf(emptyList<DownloadedItem>())).whenever(downloadRepository).observeDownloads()
        // Phase 58 : flux de changements de préférences collecté dans init{},
        // et préférences vides par défaut (aucune catégorie masquée).
        doReturn(flowOf(Unit)).whenever(categoryPreferenceRepository).changes
        doReturn(kotlinx.coroutines.flow.MutableSharedFlow<Unit>()).whenever(getRecommendationsUseCase).invalidations
        // Les tendances sont lues en deux temps (cache immédiat puis
        // rafraîchissement) : sans stub de `cached()`, l'appel suspendu du mock
        // ne reprend jamais et le test se bloque.
        whenever(getTrendingInCatalogUseCase.cached()).thenReturn(emptyList())
        whenever(getTrendingInCatalogUseCase.invoke()).thenReturn(emptyList())
        whenever(getRecommendationsUseCase.invoke(any())).thenReturn(
            com.cstv.app.domain.usecase.GetRecommendationsUseCase.RecommendationResult(emptyList(), emptyList())
        )
        whenever(getPopularTop10InCatalogUseCase.invoke()).thenReturn(popular)
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
        whenever(vodRepository.getCachedVodStreams("all")).thenReturn(emptyList())
        whenever(seriesRepository.getCachedSeriesStreams("all")).thenReturn(emptyList())

        viewModel = createViewModel()
        activeProfileId.value = 2
        testDispatcher.scheduler.runCurrent()

        verify(getLiveCategoriesUseCase, times(2)).once()

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun test_loadHomeData_success_populatesAllSections() = runTest {
        val positions = listOf(
            PlaybackPosition(1, 1000L, 50000L, System.currentTimeMillis(), "Movie 1", "cover1", "movie", "mp4")
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
        val vodStreams = listOf(VodStream(201, "Movie A", "icon2", "8.5", "2026", "1"))
        whenever(vodRepository.getCachedVodStreams("all")).thenReturn(vodStreams)

        // Mock Series
        val seriesStreams = listOf(SeriesStream(301, "Series X", "cover3", "9.0", "2026", "1"))
        whenever(seriesRepository.getCachedSeriesStreams("all")).thenReturn(seriesStreams)

        viewModel = createViewModel()

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
    fun test_loadHomeData_keepsLocalTop10AndPublishesIndependentPopularReplacement() = runTest {
        val popularMovie = VodStream(999, "Popular movie", null, null, null, "movies")
        val fallbackMovie = VodStream(100, "Fallback movie", null, "9.0", "1", "movies")
        stubReactiveSources(popular = PopularTop10Result(listOf(popularMovie), null))
        stubEmptyCategoryPreferences()
        whenever(getLiveCategoriesUseCase.once()).thenReturn(emptyList())
        whenever(vodRepository.getCachedVodStreams("all")).thenReturn(listOf(fallbackMovie))
        whenever(seriesRepository.getCachedSeriesStreams("all")).thenReturn(emptyList())

        viewModel = createViewModel()

        assertEquals(listOf(fallbackMovie), viewModel.state.value.topVodStreams)
        assertEquals(listOf(popularMovie), viewModel.state.value.popularTopVodStreams)
        assertNull(viewModel.state.value.popularTopSeriesStreams)

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun test_loadHomeData_partialFailure_keepsOtherSectionsFunctional() = runTest {
        stubReactiveSources()
        stubEmptyCategoryPreferences()

        // Mock Live TV throws exception
        whenever(getLiveCategoriesUseCase.once()).thenThrow(RuntimeException("API Error Live TV"))

        // Mock VOD Movies succeeds
        val vodStreams = listOf(VodStream(201, "Movie A", "icon2", "8.5", "2026", "1"))
        whenever(vodRepository.getCachedVodStreams("all")).thenReturn(vodStreams)

        // Mock Series to be empty
        whenever(seriesRepository.getCachedSeriesStreams("all")).thenReturn(emptyList())

        viewModel = createViewModel()

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
        val positions = listOf(
            // Série 10 : épisode le plus récent (doit être conservé)
            PlaybackPosition(102, 1000L, 50000L, 3000L, "Show A - S1E2", "cover", "series", "mp4", seriesId = 10, episodeNum = 2, seasonNum = 1),
            // Film sans seriesId : jamais regroupé
            PlaybackPosition(501, 1000L, 50000L, 2500L, "Movie 1", "cover1", "movie", "mp4"),
            // Série 10 : épisode plus ancien (doit être exclu, même série que ci-dessus)
            PlaybackPosition(101, 1000L, 50000L, 2000L, "Show A - S1E1", "cover", "series", "mp4", seriesId = 10, episodeNum = 1, seasonNum = 1),
            // Série 20 : une seule entrée, forcément conservée
            PlaybackPosition(201, 1000L, 50000L, 1000L, "Show B - S1E1", "cover", "series", "mp4", seriesId = 20, episodeNum = 1, seasonNum = 1)
        )
        stubReactiveSources(positions = positions)
        stubEmptyCategoryPreferences()
        whenever(getLiveCategoriesUseCase.once()).thenReturn(emptyList())
        whenever(vodRepository.getCachedVodStreams("all")).thenReturn(emptyList())
        whenever(seriesRepository.getCachedSeriesStreams("all")).thenReturn(emptyList())

        viewModel = createViewModel()

        val resumeWatching = viewModel.state.value.resumeWatchingList
        assertEquals(3, resumeWatching.size)
        assertTrue(resumeWatching.any { it.streamId == 102 }) // dernier épisode de la série 10
        assertFalse(resumeWatching.any { it.streamId == 101 }) // ancien épisode de la série 10, exclu
        assertTrue(resumeWatching.any { it.streamId == 501 }) // film, toujours présent
        assertTrue(resumeWatching.any { it.streamId == 201 }) // seule entrée de la série 20

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun test_loadHomeData_sortsVodAndSeriesByAddedDescending_andLimitsTo20() = runTest {
        stubReactiveSources()
        stubEmptyCategoryPreferences()
        whenever(getLiveCategoriesUseCase.once()).thenReturn(emptyList())

        // 22 streams générés avec des dates d'ajout croissantes : le plus récent (id 21, added "21")
        // doit apparaître en premier, et seuls les 20 plus récents doivent être conservés.
        val vodStreams = (0..21).map { i -> VodStream(i, "Movie $i", "icon", "5.0", i.toString(), "1") }
        val seriesStreams = (0..21).map { i -> SeriesStream(i, "Series $i", "cover", "5.0", i.toString(), "1") }
        whenever(vodRepository.getCachedVodStreams("all")).thenReturn(vodStreams)
        whenever(seriesRepository.getCachedSeriesStreams("all")).thenReturn(seriesStreams)

        viewModel = createViewModel()

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
            PlaybackPosition(101, 1000L, 50000L, System.currentTimeMillis(), "Movie 101", "cover1", "movie", "mp4", seriesId = null),
            PlaybackPosition(201, 1000L, 50000L, System.currentTimeMillis(), "Episode 201", "cover2", "series", "mp4", seriesId = 1001)
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

        // Also mock the stream category IDs:
        // Movie 101 is in "hidden_vod_cat"
        whenever(vodRepository.getCachedVodStreams("all")).thenReturn(
            listOf(VodStream(101, "Movie 101", "cover1", null, null, "hidden_vod_cat"))
        )
        whenever(seriesRepository.getCachedSeriesStreams("all")).thenReturn(
            listOf(SeriesStream(1001, "Series 201", "cover2", null, null, "visible_series_cat"))
        )

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

        // Pas d'`advanceUntilIdle` ici : le sondage EPG de `init` est une boucle
        // infinie temporisée, le scheduler n'atteindrait jamais l'inactivité.
        viewModel = createViewModel()
        testDispatcher.scheduler.runCurrent()

        val state = viewModel.state.value
        assertEquals(1, state.trendingList.size)
        assertEquals("Dune", state.trendingList[0].trendingTitle.title)
        assertEquals(false, state.awaitingTrending)

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

    private fun trendingMovie(streamId: Int, tmdbId: Int) = TrendingCatalogItem(
        trendingTitle = TrendingTitle(tmdbId, "Movie $streamId", true, 2026, null),
        matchedMovie = VodStream(streamId, "Movie $streamId", null, null, null, "movies")
    )
}
