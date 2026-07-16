package com.poc.iptvxtream.presentation.vod

import androidx.lifecycle.viewModelScope
import com.poc.iptvxtream.data.local.storage.CredentialsManager
import com.poc.iptvxtream.data.local.storage.SettingsManager
import com.poc.iptvxtream.domain.model.VodCategory
import com.poc.iptvxtream.domain.model.VodDetails
import com.poc.iptvxtream.domain.model.VodStream
import com.poc.iptvxtream.domain.repository.TrackPreferenceRepository
import com.poc.iptvxtream.domain.usecase.GetVodCategoriesUseCase
import com.poc.iptvxtream.domain.usecase.GetVodCategoryCountsUseCase
import com.poc.iptvxtream.domain.usecase.GetVodDetailsUseCase
import com.poc.iptvxtream.domain.usecase.GetVodStreamsUseCase
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
class VodViewModelTest {

    @Mock
    private lateinit var getVodCategoriesUseCase: GetVodCategoriesUseCase

    @Mock
    private lateinit var getVodCategoryCountsUseCase: GetVodCategoryCountsUseCase

    @Mock
    private lateinit var getVodStreamsUseCase: GetVodStreamsUseCase

    @Mock
    private lateinit var getVodDetailsUseCase: GetVodDetailsUseCase

    @Mock
    private lateinit var savePlaybackPositionUseCase: SavePlaybackPositionUseCase

    @Mock
    private lateinit var credentialsManager: CredentialsManager

    @Mock
    private lateinit var settingsManager: SettingsManager

    @Mock
    private lateinit var trackPreferenceRepository: TrackPreferenceRepository

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: VodViewModel

    private val category = VodCategory("1", "Action", 0)
    private val stream = VodStream(201, "Film A", null, "8.5", "2026", "1")
    private val details = VodDetails(
        streamId = 201,
        name = "Film A",
        director = "Réal",
        actors = "Acteurs",
        releaseDate = "2026",
        genre = "Action",
        plot = "Résumé",
        rating = "8.5",
        coverBig = null,
        containerExtension = "mp4"
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

    private fun createViewModel(): VodViewModel {
        val vm = VodViewModel(
            getVodCategoriesUseCase,
            getVodCategoryCountsUseCase,
            getVodStreamsUseCase,
            getVodDetailsUseCase,
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
        whenever(getVodCategoriesUseCase(false)).thenReturn(listOf(category))
        whenever(getVodStreamsUseCase("all", false)).thenReturn(listOf(stream))
        whenever(getVodCategoryCountsUseCase()).thenReturn(mapOf("1" to 42))

        viewModel = createViewModel()

        val state = viewModel.state.value
        assertEquals(listOf("all", "1"), state.categories.map { it.categoryId })
        assertEquals("all", state.selectedCategory?.categoryId)
        assertEquals(listOf(stream), state.streams)
        assertEquals(mapOf("1" to 42), state.categoryCounts)
        assertFalse(state.isLoadingCategories)
        assertFalse(state.isLoadingStreams)
        assertNull(state.error)
    }

    @Test
    fun loadCategories_erreurPropageeDansLeState() = runTest {
        whenever(getVodCategoriesUseCase(false)).thenThrow(RuntimeException("panne API"))

        viewModel = createViewModel()

        val state = viewModel.state.value
        assertEquals("panne API", state.error)
        assertFalse(state.isLoadingCategories)
    }

    @Test
    fun loadStreams_erreurPropageeDansLeState() = runTest {
        whenever(getVodCategoriesUseCase(false)).thenReturn(emptyList())
        whenever(getVodStreamsUseCase("all", false)).thenThrow(RuntimeException("timeout"))

        viewModel = createViewModel()

        val state = viewModel.state.value
        assertEquals("timeout", state.error)
        assertFalse(state.isLoadingStreams)
    }

    @Test
    fun loadStreams_cancellationExceptionNonAvaleeEnErreur() = runTest {
        whenever(getVodCategoriesUseCase(false)).thenReturn(emptyList())
        whenever(getVodStreamsUseCase("all", false)).thenThrow(CancellationException("cancelled"))

        viewModel = createViewModel()

        // La CancellationException est re-thrown (annulation normale du job),
        // jamais transformée en erreur affichée à l'utilisateur.
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun selectStream_chargeLesDetails() = runTest {
        whenever(getVodCategoriesUseCase(false)).thenReturn(emptyList())
        whenever(getVodStreamsUseCase("all", false)).thenReturn(listOf(stream))
        whenever(getVodCategoryCountsUseCase()).thenReturn(emptyMap())
        whenever(getVodDetailsUseCase(201)).thenReturn(details)
        viewModel = createViewModel()

        viewModel.selectStream(stream)
        testDispatcher.scheduler.runCurrent()

        val state = viewModel.state.value
        assertEquals(stream, state.selectedStream)
        assertEquals(details, state.selectedVodDetails)
        assertFalse(state.isLoadingDetails)
    }

    @Test
    fun selectStream_erreurDetailsPropageeDansLeState() = runTest {
        whenever(getVodCategoriesUseCase(false)).thenReturn(emptyList())
        whenever(getVodStreamsUseCase("all", false)).thenReturn(listOf(stream))
        whenever(getVodCategoryCountsUseCase()).thenReturn(emptyMap())
        whenever(getVodDetailsUseCase(201)).thenThrow(RuntimeException("détails indisponibles"))
        viewModel = createViewModel()

        viewModel.selectStream(stream)
        testDispatcher.scheduler.runCurrent()

        val state = viewModel.state.value
        assertEquals("détails indisponibles", state.error)
        assertNull(state.selectedVodDetails)
        assertFalse(state.isLoadingDetails)
    }

    @Test
    fun savePosition_metAJourLaPositionDeRepriseDesDetailsAffiches() = runTest {
        whenever(getVodCategoriesUseCase(false)).thenReturn(emptyList())
        whenever(getVodStreamsUseCase("all", false)).thenReturn(listOf(stream))
        whenever(getVodCategoryCountsUseCase()).thenReturn(emptyMap())
        whenever(getVodDetailsUseCase(201)).thenReturn(details)
        viewModel = createViewModel()
        viewModel.selectStream(stream)
        testDispatcher.scheduler.runCurrent()

        viewModel.savePosition(201, 60_000L, 7_200_000L, details)
        testDispatcher.scheduler.runCurrent()

        val updated = viewModel.state.value.selectedVodDetails
        assertEquals(60_000L, updated?.resumePositionMs)
        assertEquals(7_200_000L, updated?.durationMs)
    }
}
