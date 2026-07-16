package com.poc.iptvxtream.presentation.series

import androidx.lifecycle.viewModelScope
import com.poc.iptvxtream.data.local.storage.CredentialsManager
import com.poc.iptvxtream.data.local.storage.SettingsManager
import com.poc.iptvxtream.domain.model.SeriesCategory
import com.poc.iptvxtream.domain.model.SeriesDetails
import com.poc.iptvxtream.domain.model.SeriesStream
import com.poc.iptvxtream.domain.repository.TrackPreferenceRepository
import com.poc.iptvxtream.domain.usecase.GetSeriesCategoriesUseCase
import com.poc.iptvxtream.domain.usecase.GetSeriesCategoryCountsUseCase
import com.poc.iptvxtream.domain.usecase.GetSeriesDetailsUseCase
import com.poc.iptvxtream.domain.usecase.GetSeriesStreamsUseCase
import com.poc.iptvxtream.domain.usecase.SavePlaybackPositionUseCase
import kotlinx.coroutines.CancellationException
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
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SeriesViewModelTest {

    @Mock
    private lateinit var getSeriesCategoriesUseCase: GetSeriesCategoriesUseCase

    @Mock
    private lateinit var getSeriesCategoryCountsUseCase: GetSeriesCategoryCountsUseCase

    @Mock
    private lateinit var getSeriesStreamsUseCase: GetSeriesStreamsUseCase

    @Mock
    private lateinit var getSeriesDetailsUseCase: GetSeriesDetailsUseCase

    @Mock
    private lateinit var savePlaybackPositionUseCase: SavePlaybackPositionUseCase

    @Mock
    private lateinit var credentialsManager: CredentialsManager

    @Mock
    private lateinit var settingsManager: SettingsManager

    @Mock
    private lateinit var trackPreferenceRepository: TrackPreferenceRepository

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: SeriesViewModel

    private val category = SeriesCategory("1", "Drame", 0)
    private val stream = SeriesStream(301, "Série X", null, "9.0", "2026", "1")
    private val details = SeriesDetails(
        seriesId = 301,
        name = "Série X",
        cover = null,
        rating = "9.0",
        seasons = emptyList(),
        episodes = emptyMap()
    )

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

    private fun createViewModel(): SeriesViewModel {
        val vm = SeriesViewModel(
            getSeriesCategoriesUseCase,
            getSeriesCategoryCountsUseCase,
            getSeriesStreamsUseCase,
            getSeriesDetailsUseCase,
            savePlaybackPositionUseCase,
            credentialsManager,
            settingsManager,
            trackPreferenceRepository
        )
        testDispatcher.scheduler.runCurrent()
        return vm
    }

    @Test
    fun init_chargeCategoriesAvecToutEnTeteEtLesStreams() = runTest {
        whenever(getSeriesCategoriesUseCase(false)).thenReturn(listOf(category))
        whenever(getSeriesStreamsUseCase("all", false)).thenReturn(listOf(stream))
        whenever(getSeriesCategoryCountsUseCase()).thenReturn(mapOf("1" to 7))

        viewModel = createViewModel()

        val state = viewModel.state.value
        assertEquals(listOf("all", "1"), state.categories.map { it.categoryId })
        assertEquals("all", state.selectedCategory?.categoryId)
        assertEquals(listOf(stream), state.streams)
        assertEquals(mapOf("1" to 7), state.categoryCounts)
        assertFalse(state.isLoadingCategories)
        assertFalse(state.isLoadingStreams)
        assertNull(state.error)
    }

    @Test
    fun loadCategories_erreurPropageeDansLeState() = runTest {
        whenever(getSeriesCategoriesUseCase(false)).thenThrow(RuntimeException("panne API"))

        viewModel = createViewModel()

        val state = viewModel.state.value
        assertEquals("panne API", state.error)
        assertFalse(state.isLoadingCategories)
    }

    @Test
    fun loadStreams_erreurPropageeDansLeState() = runTest {
        whenever(getSeriesCategoriesUseCase(false)).thenReturn(emptyList())
        whenever(getSeriesStreamsUseCase("all", false)).thenThrow(RuntimeException("timeout"))

        viewModel = createViewModel()

        val state = viewModel.state.value
        assertEquals("timeout", state.error)
        assertFalse(state.isLoadingStreams)
    }

    @Test
    fun loadStreams_cancellationExceptionNonAvaleeEnErreur() = runTest {
        whenever(getSeriesCategoriesUseCase(false)).thenReturn(emptyList())
        whenever(getSeriesStreamsUseCase("all", false)).thenThrow(CancellationException("cancelled"))

        viewModel = createViewModel()

        assertNull(viewModel.state.value.error)
    }

    @Test
    fun selectCategory_chargeLesStreamsDeLaCategorie() = runTest {
        whenever(getSeriesCategoriesUseCase(false)).thenReturn(listOf(category))
        whenever(getSeriesStreamsUseCase("all", false)).thenReturn(emptyList())
        whenever(getSeriesCategoryCountsUseCase()).thenReturn(emptyMap())
        viewModel = createViewModel()

        whenever(getSeriesStreamsUseCase("1", false)).thenReturn(listOf(stream))
        viewModel.selectCategory(category)
        testDispatcher.scheduler.runCurrent()

        val state = viewModel.state.value
        assertEquals(category, state.selectedCategory)
        assertEquals(listOf(stream), state.streams)
    }

    @Test
    fun selectStream_chargeLesDetails() = runTest {
        whenever(getSeriesCategoriesUseCase(false)).thenReturn(emptyList())
        whenever(getSeriesStreamsUseCase("all", false)).thenReturn(listOf(stream))
        whenever(getSeriesCategoryCountsUseCase()).thenReturn(emptyMap())
        whenever(getSeriesDetailsUseCase(301)).thenReturn(details)
        viewModel = createViewModel()

        viewModel.selectStream(stream)
        testDispatcher.scheduler.runCurrent()

        val state = viewModel.state.value
        assertEquals(stream, state.selectedStream)
        assertEquals(details, state.selectedSeriesDetails)
        assertFalse(state.isLoadingDetails)
    }

    @Test
    fun selectStream_erreurDetailsPropageeDansLeState() = runTest {
        whenever(getSeriesCategoriesUseCase(false)).thenReturn(emptyList())
        whenever(getSeriesStreamsUseCase("all", false)).thenReturn(listOf(stream))
        whenever(getSeriesCategoryCountsUseCase()).thenReturn(emptyMap())
        whenever(getSeriesDetailsUseCase(301)).thenThrow(RuntimeException("détails indisponibles"))
        viewModel = createViewModel()

        viewModel.selectStream(stream)
        testDispatcher.scheduler.runCurrent()

        val state = viewModel.state.value
        assertEquals("détails indisponibles", state.error)
        assertNull(state.selectedSeriesDetails)
        assertFalse(state.isLoadingDetails)
    }
}
