package com.cstv.app.presentation.home

import com.cstv.app.domain.model.CategoryPreference
import com.cstv.app.domain.model.SeriesStream
import com.cstv.app.domain.model.VodStream
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import com.cstv.app.domain.model.CategoryType
import com.cstv.app.domain.repository.VodRepository
import com.cstv.app.domain.repository.SeriesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class RecentlyAddedViewModelTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (ticker infini dans un `init` de ViewModel, `advanceUntilIdle` sur une
    // tâche périodique) fige le build sans jamais échouer. Cette règle nomme le
    // test fautif ; le garde-fou dur est `tasks.withType<Test> { timeout }`
    // dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)


    private val testDispatcher = StandardTestDispatcher()

    @Mock
    private lateinit var vodRepository: VodRepository

    @Mock
    private lateinit var seriesRepository: SeriesRepository

    @Mock
    private lateinit var categoryPreferenceRepository: CategoryPreferenceRepository

    private lateinit var viewModel: RecentlyAddedViewModel

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        viewModel = RecentlyAddedViewModel(vodRepository, seriesRepository, categoryPreferenceRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun test_loadRecentlyAdded_movies_success() = runTest {
        val movies = listOf(
            VodStream(streamId = 1, name = "Movie A", streamIcon = null, rating = null, added = "200", categoryId = "cat1"),
            VodStream(streamId = 2, name = "Movie B", streamIcon = null, rating = null, added = "300", categoryId = "cat2"),
            VodStream(streamId = 3, name = "Movie C", streamIcon = null, rating = null, added = "100", categoryId = "cat3")
        )
        whenever(vodRepository.getCachedVodStreams("all")).thenReturn(movies)
        whenever(categoryPreferenceRepository.getPreferences(CategoryType.VOD)).thenReturn(emptyMap())

        viewModel.loadRecentlyAdded(false)
        testScheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(3, state.vodStreams.size)
        assertEquals(2, state.vodStreams[0].streamId) // Newest by added desc is 2 (added = 300)
        assertEquals(1, state.vodStreams[1].streamId) // 1 (added = 200)
        assertEquals(3, state.vodStreams[2].streamId) // 3 (added = 100)
        assertTrue(state.seriesStreams.isEmpty())
    }

    @Test
    fun test_loadRecentlyAdded_series_success() = runTest {
        val series = listOf(
            SeriesStream(seriesId = 1, name = "Series A", cover = null, rating = null, added = "1000", categoryId = "cat1"),
            SeriesStream(seriesId = 2, name = "Series B", cover = null, rating = null, added = "1500", categoryId = "cat2")
        )
        whenever(seriesRepository.getCachedSeriesStreams("all")).thenReturn(series)
        whenever(categoryPreferenceRepository.getPreferences(CategoryType.SERIES)).thenReturn(emptyMap())

        viewModel.loadRecentlyAdded(true)
        testScheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(2, state.seriesStreams.size)
        assertEquals(2, state.seriesStreams[0].seriesId) // Newest first
        assertEquals(1, state.seriesStreams[1].seriesId)
        assertTrue(state.vodStreams.isEmpty())
    }

    @Test
    fun test_loadRecentlyAdded_movies_excludesHiddenCategory() = runTest {
        val movies = listOf(
            VodStream(streamId = 1, name = "Movie A", streamIcon = null, rating = null, added = "300", categoryId = "cat1"),
            VodStream(streamId = 2, name = "Movie B", streamIcon = null, rating = null, added = "200", categoryId = "cat2")
        )
        whenever(vodRepository.getCachedVodStreams("all")).thenReturn(movies)
        whenever(categoryPreferenceRepository.getPreferences(CategoryType.VOD))
            .thenReturn(mapOf("cat1" to CategoryPreference("cat1", hidden = true, sortOrder = null)))

        viewModel.loadRecentlyAdded(false)
        testScheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(1, state.vodStreams.size)
        assertEquals(2, state.vodStreams[0].streamId)
    }

    @Test
    fun test_loadRecentlyAdded_series_excludesHiddenCategory() = runTest {
        val series = listOf(
            SeriesStream(seriesId = 1, name = "Series A", cover = null, rating = null, added = "1000", categoryId = "cat1"),
            SeriesStream(seriesId = 2, name = "Series B", cover = null, rating = null, added = "500", categoryId = "cat2")
        )
        whenever(seriesRepository.getCachedSeriesStreams("all")).thenReturn(series)
        whenever(categoryPreferenceRepository.getPreferences(CategoryType.SERIES))
            .thenReturn(mapOf("cat2" to CategoryPreference("cat2", hidden = true, sortOrder = null)))

        viewModel.loadRecentlyAdded(true)
        testScheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(1, state.seriesStreams.size)
        assertEquals(1, state.seriesStreams[0].seriesId)
    }

    @Test
    fun test_loadRecentlyAdded_movies_capsAt100() = runTest {
        val movies = (1..150).map {
            VodStream(streamId = it, name = "Movie $it", streamIcon = null, rating = null, added = it.toString(), categoryId = "cat1")
        }
        whenever(vodRepository.getCachedVodStreams("all")).thenReturn(movies)
        whenever(categoryPreferenceRepository.getPreferences(CategoryType.VOD)).thenReturn(emptyMap())

        viewModel.loadRecentlyAdded(false)
        testScheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(100, state.vodStreams.size)
        assertEquals(150, state.vodStreams.first().streamId) // le plus récent (added max)
    }

    @Test
    fun test_loadRecentlyAdded_repositoryThrows_setsErrorState() = runTest {
        whenever(vodRepository.getCachedVodStreams("all")).thenThrow(RuntimeException("Panel injoignable"))

        viewModel.loadRecentlyAdded(false)
        testScheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals("Panel injoignable", state.error)
        assertTrue(state.vodStreams.isEmpty())
    }

    @Test
    fun test_loadRecentlyAdded_categoryPreferenceThrows_fallsBackToNoFiltering() = runTest {
        val movies = listOf(
            VodStream(streamId = 1, name = "Movie A", streamIcon = null, rating = null, added = "100", categoryId = "cat1")
        )
        whenever(vodRepository.getCachedVodStreams("all")).thenReturn(movies)
        whenever(categoryPreferenceRepository.getPreferences(CategoryType.VOD)).thenThrow(RuntimeException("DB error"))

        viewModel.loadRecentlyAdded(false)
        testScheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(1, state.vodStreams.size)
    }
}
