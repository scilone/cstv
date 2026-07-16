package com.poc.iptvxtream.presentation.home

import com.poc.iptvxtream.domain.model.*
import com.poc.iptvxtream.domain.repository.FavoritesRepository
import com.poc.iptvxtream.domain.repository.LiveTvRepository
import com.poc.iptvxtream.domain.repository.SeriesRepository
import com.poc.iptvxtream.domain.repository.VodRepository
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
    private lateinit var getLiveEpgUseCase: com.poc.iptvxtream.domain.usecase.GetLiveEpgUseCase

    @Mock
    private lateinit var getLiveCategoriesUseCase: com.poc.iptvxtream.domain.usecase.GetLiveCategoriesUseCase

    @Mock
    private lateinit var categoryPreferenceRepository: com.poc.iptvxtream.domain.repository.CategoryPreferenceRepository

    // Phase 42 : StandardTestDispatcher (et non Unconfined) + runCurrent() après
    // construction. HomeViewModel lance désormais un ticker EPG infini
    // (while(true) { delay(60s); ... }) dans son init : avec un dispatcher
    // "unconfined" autonome (scheduler non lié à celui de runTest), ce ticker
    // provoque un blocage réel du test. runCurrent() n'exécute que le travail
    // déjà dû à l'instant virtuel courant (le chargement initial), sans jamais
    // avancer jusqu'au premier délai de 60s du ticker.
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
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
            getLiveEpgUseCase,
            getLiveCategoriesUseCase,
            categoryPreferenceRepository
        )
        testDispatcher.scheduler.runCurrent()
        return vm
    }

    // Phase 41 : resume/favorites viennent maintenant de Flows continus
    // (observeAllPlaybackPositions/observeFavorites) plutôt que d'un fetch
    // ponctuel dans loadHomeData ; ce helper les stub par défaut à vide.
    private fun stubReactiveSources(
        positions: List<PlaybackPosition> = emptyList(),
        favorites: List<FavoriteItem> = emptyList()
    ) {
        doReturn(flowOf(positions)).whenever(vodRepository).observeAllPlaybackPositions()
        doReturn(flowOf(favorites)).whenever(favoritesRepository).observeFavorites()
        // Phase 58 : flux de changements de préférences collecté dans init{},
        // et préférences vides par défaut (aucune catégorie masquée).
        doReturn(kotlinx.coroutines.flow.emptyFlow<Unit>()).whenever(categoryPreferenceRepository).changes
    }

    private suspend fun stubEmptyCategoryPreferences() {
        whenever(categoryPreferenceRepository.getPreferences(any())).thenReturn(emptyMap())
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
        whenever(getLiveCategoriesUseCase(false)).thenReturn(liveCats)
        whenever(liveTvRepository.getLiveStreams("1", false)).thenReturn(liveStreams)

        // Mock VOD Movies
        val vodStreams = listOf(VodStream(201, "Movie A", "icon2", "8.5", "2026", "1"))
        whenever(vodRepository.getVodStreams("all", false)).thenReturn(vodStreams)

        // Mock Series
        val seriesStreams = listOf(SeriesStream(301, "Series X", "cover3", "9.0", "2026", "1"))
        whenever(seriesRepository.getSeriesStreams("all", false)).thenReturn(seriesStreams)

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
    fun test_loadHomeData_partialFailure_keepsOtherSectionsFunctional() = runTest {
        stubReactiveSources()
        stubEmptyCategoryPreferences()

        // Mock Live TV throws exception
        whenever(getLiveCategoriesUseCase(false)).thenThrow(RuntimeException("API Error Live TV"))

        // Mock VOD Movies succeeds
        val vodStreams = listOf(VodStream(201, "Movie A", "icon2", "8.5", "2026", "1"))
        whenever(vodRepository.getVodStreams("all", false)).thenReturn(vodStreams)

        // Mock Series to be empty
        whenever(seriesRepository.getSeriesStreams("all", false)).thenReturn(emptyList())

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
        whenever(getLiveCategoriesUseCase(false)).thenReturn(emptyList())
        whenever(vodRepository.getVodStreams("all", false)).thenReturn(emptyList())
        whenever(seriesRepository.getSeriesStreams("all", false)).thenReturn(emptyList())

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
        whenever(getLiveCategoriesUseCase(false)).thenReturn(emptyList())

        // 22 streams générés avec des dates d'ajout croissantes : le plus récent (id 21, added "21")
        // doit apparaître en premier, et seuls les 20 plus récents doivent être conservés.
        val vodStreams = (0..21).map { i -> VodStream(i, "Movie $i", "icon", "5.0", i.toString(), "1") }
        val seriesStreams = (0..21).map { i -> SeriesStream(i, "Series $i", "cover", "5.0", i.toString(), "1") }
        whenever(vodRepository.getVodStreams("all", false)).thenReturn(vodStreams)
        whenever(seriesRepository.getSeriesStreams("all", false)).thenReturn(seriesStreams)

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
        whenever(getLiveCategoriesUseCase(false)).thenReturn(liveCats)
        whenever(liveTvRepository.getLiveStreams("1", false)).thenReturn(liveStreams)
        whenever(vodRepository.getVodStreams("all", false)).thenReturn(emptyList())
        whenever(seriesRepository.getSeriesStreams("all", false)).thenReturn(emptyList())

        viewModel = createViewModel()

        val state = viewModel.state.value
        // TV en direct garde la logique "première catégorie" (pas de notion d'ajout exploitable)
        assertEquals("1", state.firstLiveCategory?.categoryId)
        assertEquals(1, state.firstLiveStreams.size)

        viewModel.viewModelScope.cancel()
    }
}
