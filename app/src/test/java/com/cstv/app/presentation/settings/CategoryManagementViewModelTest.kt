package com.cstv.app.presentation.settings

import com.cstv.app.domain.model.CategoryPreference
import com.cstv.app.domain.model.CategoryType
import com.cstv.app.domain.model.LiveCategory
import com.cstv.app.domain.model.VodCategory
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import com.cstv.app.domain.repository.LiveTvRepository
import com.cstv.app.domain.repository.SeriesRepository
import com.cstv.app.domain.repository.VodRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryManagementViewModelTest {

    @Mock
    private lateinit var liveTvRepository: LiveTvRepository

    @Mock
    private lateinit var vodRepository: VodRepository

    @Mock
    private lateinit var seriesRepository: SeriesRepository

    @Mock
    private lateinit var categoryPreferenceRepository: CategoryPreferenceRepository

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): CategoryManagementViewModel {
        val vm = CategoryManagementViewModel(
            liveTvRepository,
            vodRepository,
            seriesRepository,
            categoryPreferenceRepository
        )
        testDispatcher.scheduler.runCurrent()
        return vm
    }

    private suspend fun stubLiveCategories() {
        whenever(liveTvRepository.getCachedLiveCategories()).thenReturn(
            listOf(
                LiveCategory("1", "Sports", 0),
                LiveCategory("2", "Cinéma", 0),
                LiveCategory("3", "Infos", 0)
            )
        )
    }

    @Test
    fun test_init_loadsLiveCategories_includingHidden_inEffectiveOrder() = runTest {
        stubLiveCategories()
        whenever(categoryPreferenceRepository.getPreferences(CategoryType.LIVE)).thenReturn(
            mapOf(
                "1" to CategoryPreference("1", hidden = true, sortOrder = 2),
                "2" to CategoryPreference("2", hidden = false, sortOrder = 0),
                "3" to CategoryPreference("3", hidden = false, sortOrder = 1)
            )
        )

        val viewModel = createViewModel()

        val state = viewModel.state.value
        assertEquals(CategoryType.LIVE, state.selectedType)
        // Les masquées restent listées (contrairement aux grilles), à leur position.
        assertEquals(listOf("Cinéma", "Infos", "Sports"), state.categories.map { it.categoryName })
        assertEquals(listOf(false, false, true), state.categories.map { it.hidden })
    }

    @Test
    fun test_selectType_switchesToVodCategories() = runTest {
        stubLiveCategories()
        whenever(categoryPreferenceRepository.getPreferences(any())).thenReturn(emptyMap())
        whenever(vodRepository.getCachedVodCategories()).thenReturn(
            listOf(VodCategory("10", "Action", 0))
        )

        val viewModel = createViewModel()
        viewModel.selectType(CategoryType.VOD)
        testDispatcher.scheduler.runCurrent()

        val state = viewModel.state.value
        assertEquals(CategoryType.VOD, state.selectedType)
        assertEquals(listOf("Action"), state.categories.map { it.categoryName })
    }

    @Test
    fun test_toggleHidden_updatesState_andPersists() = runTest {
        stubLiveCategories()
        whenever(categoryPreferenceRepository.getPreferences(CategoryType.LIVE)).thenReturn(emptyMap())

        val viewModel = createViewModel()
        viewModel.toggleHidden("2")
        testDispatcher.scheduler.runCurrent()

        assertEquals(
            true,
            viewModel.state.value.categories.find { it.categoryId == "2" }?.hidden
        )
        verify(categoryPreferenceRepository).setHidden(CategoryType.LIVE, "2", true)
    }

    @Test
    fun test_moveDown_reorders_andSavesFullOrder() = runTest {
        stubLiveCategories()
        whenever(categoryPreferenceRepository.getPreferences(CategoryType.LIVE)).thenReturn(emptyMap())

        val viewModel = createViewModel()
        viewModel.moveDown("1")
        testDispatcher.scheduler.runCurrent()

        assertEquals(
            listOf("2", "1", "3"),
            viewModel.state.value.categories.map { it.categoryId }
        )
        verify(categoryPreferenceRepository).saveOrder(CategoryType.LIVE, listOf("2", "1", "3"))
    }

    @Test
    fun test_moveUp_atTop_isNoOp() = runTest {
        stubLiveCategories()
        whenever(categoryPreferenceRepository.getPreferences(CategoryType.LIVE)).thenReturn(emptyMap())

        val viewModel = createViewModel()
        viewModel.moveUp("1")
        testDispatcher.scheduler.runCurrent()

        assertEquals(
            listOf("1", "2", "3"),
            viewModel.state.value.categories.map { it.categoryId }
        )
        verify(categoryPreferenceRepository, never()).saveOrder(any(), any())
    }

    @Test
    fun test_loadFailure_setsErrorMessage() = runTest {
        whenever(liveTvRepository.getCachedLiveCategories()).thenThrow(RuntimeException("boom"))

        val viewModel = createViewModel()

        assertNotNull(viewModel.state.value.error)
        assertEquals(false, viewModel.state.value.isLoading)
    }
}
