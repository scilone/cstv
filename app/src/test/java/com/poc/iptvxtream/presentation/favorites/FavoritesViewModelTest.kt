package com.poc.iptvxtream.presentation.favorites

import androidx.lifecycle.viewModelScope
import com.poc.iptvxtream.domain.model.FavoriteItem
import com.poc.iptvxtream.domain.model.SearchResult
import com.poc.iptvxtream.domain.model.VodStream
import com.poc.iptvxtream.domain.usecase.AddFavoriteUseCase
import com.poc.iptvxtream.domain.usecase.IsFavoriteUseCase
import com.poc.iptvxtream.domain.usecase.ObserveFavoritesUseCase
import com.poc.iptvxtream.domain.usecase.RemoveFavoriteUseCase
import com.poc.iptvxtream.domain.usecase.SearchUnifiedUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

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

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: FavoritesViewModel

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

    private fun createViewModel(
        favorites: List<FavoriteItem> = emptyList()
    ): FavoritesViewModel {
        doReturn(flowOf(favorites)).whenever(observeFavoritesUseCase).invoke()
        val vm = FavoritesViewModel(
            observeFavoritesUseCase,
            addFavoriteUseCase,
            removeFavoriteUseCase,
            isFavoriteUseCase,
            searchUnifiedUseCase
        )
        testDispatcher.scheduler.runCurrent()
        return vm
    }

    @Test
    fun init_collecteLeFlowDeFavoris() = runTest {
        val favorites = listOf(FavoriteItem(1, "live", "Chaîne", null, "cat1"))
        viewModel = createViewModel(favorites)

        val state = viewModel.state.value
        assertEquals(favorites, state.favorites)
        assertFalse(state.isLoadingFavorites)
    }

    @Test
    fun recherche_attendLeDebounceAvantDeChercher() = runTest {
        whenever(searchUnifiedUseCase("matrix")).thenReturn(
            SearchResult(vodResults = listOf(VodStream(1, "Matrix", null, "9", "1999", "c")))
        )
        viewModel = createViewModel()

        viewModel.onSearchQueryChanged("matrix")
        testDispatcher.scheduler.runCurrent()
        // Avant l'échéance du debounce (300 ms) : pas d'appel.
        verify(searchUnifiedUseCase, never()).invoke(any())
        assertTrue(viewModel.state.value.isSearching)

        testDispatcher.scheduler.advanceTimeBy(300)
        testDispatcher.scheduler.runCurrent()

        verify(searchUnifiedUseCase).invoke("matrix")
        val state = viewModel.state.value
        assertFalse(state.isSearching)
        assertEquals(1, state.searchResult.vodResults.size)
        assertEquals("Matrix", state.searchResult.vodResults[0].name)
    }

    @Test
    fun recherche_uneNouvelleFrappeAnnuleLaPrecedente() = runTest {
        whenever(searchUnifiedUseCase("ab")).thenReturn(SearchResult())
        viewModel = createViewModel()

        viewModel.onSearchQueryChanged("a")
        testDispatcher.scheduler.advanceTimeBy(150) // avant l'échéance du debounce
        viewModel.onSearchQueryChanged("ab")
        testDispatcher.scheduler.advanceTimeBy(300)
        testDispatcher.scheduler.runCurrent()

        // La requête obsolète "a" ne part jamais, seule "ab" est exécutée.
        verify(searchUnifiedUseCase, never()).invoke("a")
        verify(searchUnifiedUseCase).invoke("ab")
        assertEquals("ab", viewModel.state.value.searchQuery)
    }

    @Test
    fun recherche_unResultatObsoleteNecrasePasLePlusRecent() = runTest {
        val resultB = SearchResult(vodResults = listOf(VodStream(2, "B", null, "9", "2020", "c")))
        whenever(searchUnifiedUseCase("b")).thenReturn(resultB)
        viewModel = createViewModel()

        // Recherche "a" lancée puis remplacée pendant son debounce : son job est
        // annulé, son résultat (même s'il avait été plus lent) ne peut plus
        // écraser celui de "b".
        viewModel.onSearchQueryChanged("a")
        testDispatcher.scheduler.advanceTimeBy(299)
        viewModel.onSearchQueryChanged("b")
        testDispatcher.scheduler.advanceTimeBy(300)
        testDispatcher.scheduler.runCurrent()

        assertEquals(resultB, viewModel.state.value.searchResult)
        verify(searchUnifiedUseCase, never()).invoke("a")
    }

    @Test
    fun recherche_requeteVideRemetLEtatAZero() = runTest {
        whenever(searchUnifiedUseCase("x")).thenReturn(
            SearchResult(vodResults = listOf(VodStream(1, "X", null, "9", "2020", "c")))
        )
        viewModel = createViewModel()
        viewModel.onSearchQueryChanged("x")
        testDispatcher.scheduler.advanceTimeBy(300)
        testDispatcher.scheduler.runCurrent()
        assertFalse(viewModel.state.value.searchResult.isEmpty)

        viewModel.onSearchQueryChanged("   ")
        testDispatcher.scheduler.runCurrent()

        val state = viewModel.state.value
        assertTrue(state.searchResult.isEmpty)
        assertFalse(state.isSearching)
    }

    @Test
    fun toggleFavorite_ajouteQuandAbsent() = runTest {
        whenever(isFavoriteUseCase(1, "movie")).thenReturn(false)
        viewModel = createViewModel()

        viewModel.toggleFavorite(1, "movie", "Film", "cover", "cat")
        testDispatcher.scheduler.runCurrent()

        verify(addFavoriteUseCase).invoke(FavoriteItem(1, "movie", "Film", "cover", "cat"))
        verify(removeFavoriteUseCase, never()).invoke(any(), any())
    }

    @Test
    fun toggleFavorite_retireQuandPresent() = runTest {
        whenever(isFavoriteUseCase(1, "movie")).thenReturn(true)
        viewModel = createViewModel()

        viewModel.toggleFavorite(1, "movie", "Film", "cover", "cat")
        testDispatcher.scheduler.runCurrent()

        verify(removeFavoriteUseCase).invoke(1, "movie")
        verify(addFavoriteUseCase, never()).invoke(any())
    }
}
