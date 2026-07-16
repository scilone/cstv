package com.poc.iptvxtream.presentation.livetv

import androidx.lifecycle.viewModelScope
import com.poc.iptvxtream.data.local.storage.CredentialsManager
import com.poc.iptvxtream.domain.model.LiveCategory
import com.poc.iptvxtream.domain.model.LiveEpgProgram
import com.poc.iptvxtream.domain.model.LiveStream
import com.poc.iptvxtream.domain.usecase.GetLiveCategoriesUseCase
import com.poc.iptvxtream.domain.usecase.GetLiveCategoryCountsUseCase
import com.poc.iptvxtream.domain.usecase.GetLiveEpgUseCase
import com.poc.iptvxtream.domain.usecase.GetLiveStreamsUseCase
import com.poc.iptvxtream.domain.usecase.GetRecentlyWatchedUseCase
import com.poc.iptvxtream.domain.usecase.SaveRecentlyWatchedUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class LiveTvViewModelTest {

    @Mock
    private lateinit var getLiveCategoriesUseCase: GetLiveCategoriesUseCase

    @Mock
    private lateinit var getLiveCategoryCountsUseCase: GetLiveCategoryCountsUseCase

    @Mock
    private lateinit var getLiveStreamsUseCase: GetLiveStreamsUseCase

    @Mock
    private lateinit var getRecentlyWatchedUseCase: GetRecentlyWatchedUseCase

    @Mock
    private lateinit var saveRecentlyWatchedUseCase: SaveRecentlyWatchedUseCase

    @Mock
    private lateinit var getLiveEpgUseCase: GetLiveEpgUseCase

    @Mock
    private lateinit var credentialsManager: CredentialsManager

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: LiveTvViewModel

    private val category = LiveCategory("1", "Sport", 0)
    private val stream = LiveStream(101, "Chaîne 1", null, null, 1, "1")

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

    private fun createViewModel(): LiveTvViewModel {
        val vm = LiveTvViewModel(
            getLiveCategoriesUseCase,
            getLiveCategoryCountsUseCase,
            getLiveStreamsUseCase,
            getRecentlyWatchedUseCase,
            saveRecentlyWatchedUseCase,
            getLiveEpgUseCase,
            credentialsManager
        )
        testDispatcher.scheduler.runCurrent()
        return vm
    }

    @Test
    fun init_chargeCategoriesAvecToutEnTeteEtLesStreams() = runTest {
        whenever(getLiveCategoriesUseCase(false)).thenReturn(listOf(category))
        whenever(getLiveStreamsUseCase("all", false)).thenReturn(listOf(stream))
        whenever(getLiveCategoryCountsUseCase()).thenReturn(mapOf("1" to 12))
        whenever(getRecentlyWatchedUseCase()).thenReturn(listOf(stream))

        viewModel = createViewModel()

        val state = viewModel.state.value
        assertEquals(listOf("all", "1"), state.categories.map { it.categoryId })
        assertEquals("all", state.selectedCategory?.categoryId)
        assertEquals(listOf(stream), state.streams)
        assertEquals(mapOf("1" to 12), state.categoryCounts)
        assertEquals(listOf(stream), state.recentlyWatched)
        assertFalse(state.isLoadingCategories)
        assertFalse(state.isLoadingStreams)
        assertNull(state.error)
    }

    @Test
    fun loadCategories_erreurPropageeDansLeState() = runTest {
        whenever(getLiveCategoriesUseCase(false)).thenThrow(RuntimeException("panne API"))
        whenever(getRecentlyWatchedUseCase()).thenReturn(emptyList())

        viewModel = createViewModel()

        val state = viewModel.state.value
        assertEquals("panne API", state.error)
        assertFalse(state.isLoadingCategories)
    }

    @Test
    fun selectCategory_chargeLesStreamsDeLaCategorie() = runTest {
        whenever(getLiveCategoriesUseCase(false)).thenReturn(listOf(category))
        whenever(getLiveStreamsUseCase("all", false)).thenReturn(emptyList())
        whenever(getLiveCategoryCountsUseCase()).thenReturn(emptyMap())
        whenever(getRecentlyWatchedUseCase()).thenReturn(emptyList())
        viewModel = createViewModel()

        whenever(getLiveStreamsUseCase("1", false)).thenReturn(listOf(stream))
        viewModel.selectCategory(category)
        testDispatcher.scheduler.runCurrent()

        val state = viewModel.state.value
        assertEquals(category, state.selectedCategory)
        assertEquals(listOf(stream), state.streams)
    }

    @Test
    fun loadEpg_pasDeDoubleFetchInFlight() = runTest {
        whenever(getLiveCategoriesUseCase(false)).thenReturn(emptyList())
        whenever(getLiveStreamsUseCase("all", false)).thenReturn(emptyList())
        whenever(getLiveCategoryCountsUseCase()).thenReturn(emptyMap())
        whenever(getRecentlyWatchedUseCase()).thenReturn(emptyList())
        val program = LiveEpgProgram("Match", null, 0L, Long.MAX_VALUE / 1000L)
        whenever(getLiveEpgUseCase(101, false)).thenReturn(program)
        viewModel = createViewModel()

        // Deux demandes avant que la première coroutine ne s'exécute :
        // la seconde est ignorée (garde in-flight + throttle 60 s).
        viewModel.loadEpgForStream(101)
        viewModel.loadEpgForStream(101)
        testDispatcher.scheduler.runCurrent()

        verify(getLiveEpgUseCase, times(1)).invoke(101, false)
        assertEquals(program, viewModel.state.value.epgPrograms[101])

        // Programme en cache toujours en cours : pas de re-fetch non plus.
        viewModel.loadEpgForStream(101)
        testDispatcher.scheduler.runCurrent()
        verify(getLiveEpgUseCase, times(1)).invoke(101, false)
    }

    @Test
    fun saveRecentlyWatched_rafraichitLaListe() = runTest {
        whenever(getLiveCategoriesUseCase(false)).thenReturn(emptyList())
        whenever(getLiveStreamsUseCase("all", false)).thenReturn(emptyList())
        whenever(getLiveCategoryCountsUseCase()).thenReturn(emptyMap())
        whenever(getRecentlyWatchedUseCase()).thenReturn(emptyList())
        viewModel = createViewModel()
        assertEquals(emptyList<LiveStream>(), viewModel.state.value.recentlyWatched)

        whenever(getRecentlyWatchedUseCase()).thenReturn(listOf(stream))
        viewModel.saveRecentlyWatched(stream)
        testDispatcher.scheduler.runCurrent()

        verify(saveRecentlyWatchedUseCase).invoke(stream)
        assertEquals(listOf(stream), viewModel.state.value.recentlyWatched)
    }
}
