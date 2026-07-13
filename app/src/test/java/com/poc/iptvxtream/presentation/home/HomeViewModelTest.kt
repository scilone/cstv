package com.poc.iptvxtream.presentation.home

import com.poc.iptvxtream.domain.model.*
import com.poc.iptvxtream.domain.repository.FavoritesRepository
import com.poc.iptvxtream.domain.repository.LiveTvRepository
import com.poc.iptvxtream.domain.repository.SeriesRepository
import com.poc.iptvxtream.domain.repository.VodRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
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

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun test_loadHomeData_success_populatesAllSections() = runTest {
        // Mock Resume Watching
        val positions = listOf(
            PlaybackPosition(1, 1000L, 50000L, System.currentTimeMillis(), "Movie 1", "cover1", "movie", "mp4")
        )
        whenever(vodRepository.getAllPlaybackPositions()).thenReturn(positions)

        // Mock Favorites
        val favorites = listOf(
            FavoriteItem(2, "live", "Live 1", "cover2", "cat1")
        )
        whenever(favoritesRepository.getFavorites()).thenReturn(favorites)

        // Mock Live TV
        val liveCats = listOf(LiveCategory("1", "Live Cat 1", 0))
        val liveStreams = listOf(LiveStream(101, "Channel 1", "icon1", null, 1, "1"))
        whenever(liveTvRepository.getLiveCategories(false)).thenReturn(liveCats)
        whenever(liveTvRepository.getLiveStreams("1", false)).thenReturn(liveStreams)

        // Mock VOD Movies
        val vodCats = listOf(VodCategory("1", "VOD Cat 1", 0))
        val vodStreams = listOf(VodStream(201, "Movie A", "icon2", "8.5", "2026", "1"))
        whenever(vodRepository.getVodCategories(false)).thenReturn(vodCats)
        whenever(vodRepository.getVodStreams("1", false)).thenReturn(vodStreams)

        // Mock Series
        val seriesCats = listOf(SeriesCategory("1", "Series Cat 1", 0))
        val seriesStreams = listOf(SeriesStream(301, "Series X", "cover3", "9.0", "2026", "1"))
        whenever(seriesRepository.getSeriesCategories(false)).thenReturn(seriesCats)
        whenever(seriesRepository.getSeriesStreams("1", false)).thenReturn(seriesStreams)

        viewModel = HomeViewModel(vodRepository, liveTvRepository, seriesRepository, favoritesRepository, getLiveEpgUseCase)

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
        assertEquals("1", state.firstVodCategory?.categoryId)
        assertEquals(1, state.firstVodStreams.size)
        assertEquals("Movie A", state.firstVodStreams[0].name)
        assertEquals("1", state.firstSeriesCategory?.categoryId)
        assertEquals(1, state.firstSeriesStreams.size)
        assertEquals("Series X", state.firstSeriesStreams[0].name)
    }

    @Test
    fun test_loadHomeData_partialFailure_keepsOtherSectionsFunctional() = runTest {
        // Mock Resume Watching & Favorites to be empty
        whenever(vodRepository.getAllPlaybackPositions()).thenReturn(emptyList())
        whenever(favoritesRepository.getFavorites()).thenReturn(emptyList())

        // Mock Live TV throws exception
        whenever(liveTvRepository.getLiveCategories(false)).thenThrow(RuntimeException("API Error Live TV"))

        // Mock VOD Movies succeeds
        val vodCats = listOf(VodCategory("1", "VOD Cat 1", 0))
        val vodStreams = listOf(VodStream(201, "Movie A", "icon2", "8.5", "2026", "1"))
        whenever(vodRepository.getVodCategories(false)).thenReturn(vodCats)
        whenever(vodRepository.getVodStreams("1", false)).thenReturn(vodStreams)

        // Mock Series to be empty
        whenever(seriesRepository.getSeriesCategories(false)).thenReturn(emptyList())

        viewModel = HomeViewModel(vodRepository, liveTvRepository, seriesRepository, favoritesRepository, getLiveEpgUseCase)

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNull(state.error) // Should not fail completely
        assertTrue(state.resumeWatchingList.isEmpty())
        assertTrue(state.favoritesList.isEmpty())
        assertNull(state.firstLiveCategory)
        assertTrue(state.firstLiveStreams.isEmpty())
        
        // VOD should be loaded successfully
        assertEquals("1", state.firstVodCategory?.categoryId)
        assertEquals(1, state.firstVodStreams.size)
        assertEquals("Movie A", state.firstVodStreams[0].name)
    }
}
