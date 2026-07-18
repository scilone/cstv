package com.cstv.app.presentation.favorites

import com.cstv.app.domain.model.*
import com.cstv.app.domain.usecase.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModelTest {

    @Mock
    private lateinit var observeFavoritesUseCase: ObserveFavoritesUseCase

    @Mock
    private lateinit var addFavoriteUseCase: AddFavoriteUseCase

    @Mock
    private lateinit var removeFavoriteUseCase: RemoveFavoriteUseCase

    @Mock
    private lateinit var isFavoriteUseCase: IsFavoriteUseCase

    @Mock
    private lateinit var searchUnifiedUseCase: SearchUnifiedUseCase

    @Mock
    private lateinit var getTopGenresUseCase: GetTopGenresUseCase

    @Mock
    private lateinit var getCategoriesForTypeUseCase: GetCategoriesForTypeUseCase

    @Mock
    private lateinit var advancedCatalogSearchUseCase: AdvancedCatalogSearchUseCase

    @Mock
    private lateinit var getCatalogYearRangeUseCase: GetCatalogYearRangeUseCase

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: FavoritesViewModel

    @Before
    fun setUp() = runTest(testDispatcher) {
        MockitoAnnotations.openMocks(this@FavoritesViewModelTest)
        Dispatchers.setMain(testDispatcher)

        whenever(observeFavoritesUseCase()).thenReturn(flowOf(emptyList()))
        whenever(getTopGenresUseCase()).thenReturn(listOf("Action", "Comedy"))
        whenever(getCategoriesForTypeUseCase(anyOrNull())).thenReturn(listOf(CategoryWithCount("all", "Toutes les catégories", 10)))
        whenever(getCatalogYearRangeUseCase()).thenReturn(1990..2024)
        whenever(advancedCatalogSearchUseCase(anyOrNull(), any())).thenReturn(
            SearchResult(
                vodResults = listOf(VodStream(1, "Movie A", null, null, null, "cat1")),
                seriesResults = listOf(SeriesStream(2, "Series B", null, null, null, "cat2"))
            )
        )

        viewModel = FavoritesViewModel(
            observeFavoritesUseCase,
            addFavoriteUseCase,
            removeFavoriteUseCase,
            isFavoriteUseCase,
            searchUnifiedUseCase,
            getTopGenresUseCase,
            getCategoriesForTypeUseCase,
            advancedCatalogSearchUseCase,
            getCatalogYearRangeUseCase
        )
        runCurrent()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun test_initialState_loadsTopGenres() = runTest(testDispatcher) {
        runCurrent()
        val state = viewModel.state.value
        assertEquals(listOf("Action", "Comedy"), state.availableGenres)
        assertFalse(state.isFilterSheetOpen)
        assertEquals(0, state.filteredResultCount)
        assertEquals(1990..2024, state.catalogYearRange)
    }

    @Test
    fun test_setFilterSheetOpen_triggersRealtimeCount() = runTest(testDispatcher) {
        viewModel.setFilterSheetOpen(true)
        assertTrue(viewModel.state.value.isFilterSheetOpen)

        // Advance time for count debounce (150ms)
        advanceTimeBy(200)
        runCurrent()

        assertEquals(2, viewModel.state.value.filteredResultCount)
    }

    @Test
    fun test_setMediaType_rechargesCategoriesAndResetsCategory() = runTest(testDispatcher) {
        // Set category to something
        viewModel.setCategory("cat1")
        assertEquals("cat1", viewModel.state.value.advancedFilter.categoryId)

        // Set media type
        viewModel.setMediaType(SearchMediaType.FILM)
        assertEquals(SearchMediaType.FILM, viewModel.state.value.advancedFilter.mediaType)
        assertNull(viewModel.state.value.advancedFilter.categoryId) // category must be reset to null

        advanceTimeBy(200)
        runCurrent()

        // Verify categories loaded
        assertEquals(listOf(CategoryWithCount("all", "Toutes les catégories", 10)), viewModel.state.value.availableCategories)
    }

    @Test
    fun test_setMinRating_triggersRealtimeCount() = runTest(testDispatcher) {
        viewModel.setMinRating(8)
        assertEquals(8, viewModel.state.value.advancedFilter.minRating)

        advanceTimeBy(200)
        runCurrent()

        assertEquals(2, viewModel.state.value.filteredResultCount)
    }

    @Test
    fun test_setYearRange_triggersRealtimeCount() = runTest(testDispatcher) {
        viewModel.setYearRange(1995..2010)
        assertEquals(1995..2010, viewModel.state.value.advancedFilter.yearRange)

        advanceTimeBy(200)
        runCurrent()

        assertEquals(2, viewModel.state.value.filteredResultCount)
    }

    @Test
    fun test_setYearRange_fullCatalogRange_normalizesToNull() = runTest(testDispatcher) {
        // catalogYearRange mocké à 1990..2024 (voir setUp) : une sélection qui
        // couvre tout le catalogue équivaut à "pas de filtre".
        viewModel.setYearRange(1990..2024)
        assertNull(viewModel.state.value.advancedFilter.yearRange)
        assertTrue(viewModel.state.value.advancedFilter.isEmpty)
    }

    @Test
    fun test_toggleGenre_triggersRealtimeCount() = runTest(testDispatcher) {
        viewModel.toggleGenre("Action")
        assertEquals(setOf("Action"), viewModel.state.value.advancedFilter.genres)

        viewModel.toggleGenre("Action")
        assertTrue(viewModel.state.value.advancedFilter.genres.isEmpty())

        advanceTimeBy(200)
        runCurrent()

        assertEquals(2, viewModel.state.value.filteredResultCount)
    }

    @Test
    fun test_resetFilter_revertsToDefault() = runTest(testDispatcher) {
        viewModel.setMediaType(SearchMediaType.SERIE)
        viewModel.setCategory("cat1")
        viewModel.setMinRating(9)

        viewModel.resetFilter()

        val filter = viewModel.state.value.advancedFilter
        assertTrue(filter.isEmpty)
        assertNull(filter.mediaType)
        assertNull(filter.categoryId)
        assertNull(filter.minRating)
    }

    @Test
    fun test_applyFilter_closesSheetAndPerformsSearch() = runTest(testDispatcher) {
        viewModel.setFilterSheetOpen(true)
        viewModel.setMediaType(SearchMediaType.FILM)

        viewModel.applyFilter()

        assertFalse(viewModel.state.value.isFilterSheetOpen)

        // Advance time for search debounce (300ms)
        advanceTimeBy(400)
        runCurrent()

        verify(advancedCatalogSearchUseCase, atLeastOnce()).invoke(anyOrNull(), any())
    }

    @Test
    fun test_applyFilter_withNoFilterAndNoQuery_browsesAllCatalog() = runTest(testDispatcher) {
        // Aucun filtre actif, aucune query : cliquer "Voir les résultats" doit
        // quand même renvoyer tout le catalogue (Films+Séries), pas rien.
        assertTrue(viewModel.state.value.advancedFilter.isEmpty)
        assertEquals("", viewModel.state.value.searchQuery)

        viewModel.applyFilter()
        advanceTimeBy(400)
        runCurrent()

        assertTrue(viewModel.state.value.hasBrowsedAll)
        assertFalse(viewModel.state.value.searchResult.isEmpty)
        assertEquals(1, viewModel.state.value.searchResult.vodResults.size)
        assertEquals(1, viewModel.state.value.searchResult.seriesResults.size)
    }

    @Test
    fun test_removeIndividualFilters_refreshesSearch() = runTest(testDispatcher) {
        viewModel.setMediaType(SearchMediaType.FILM)
        viewModel.setCategory("cat1")
        viewModel.setMinRating(7)

        viewModel.removeCategoryFilter()
        assertNull(viewModel.state.value.advancedFilter.categoryId)

        advanceTimeBy(400)
        runCurrent()

        verify(advancedCatalogSearchUseCase, atLeastOnce()).invoke(anyOrNull(), any())
    }
}
