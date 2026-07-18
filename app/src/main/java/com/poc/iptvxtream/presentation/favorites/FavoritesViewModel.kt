package com.poc.iptvxtream.presentation.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poc.iptvxtream.domain.model.AdvancedSearchFilter
import com.poc.iptvxtream.domain.model.CategoryWithCount
import com.poc.iptvxtream.domain.model.FavoriteItem
import com.poc.iptvxtream.domain.model.SearchMediaType
import com.poc.iptvxtream.domain.model.SearchResult
import com.poc.iptvxtream.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SEARCH_DEBOUNCE_MILLIS = 300L

data class FavoritesUiState(
    val favorites: List<FavoriteItem> = emptyList(),
    val searchResult: SearchResult = SearchResult(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val isLoadingFavorites: Boolean = true,
    val advancedFilter: AdvancedSearchFilter = AdvancedSearchFilter.DEFAULT,
    val isFilterSheetOpen: Boolean = false,
    val availableGenres: List<String> = emptyList(),
    val availableCategories: List<CategoryWithCount> = emptyList(),
    val filteredResultCount: Int = 0
)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val observeFavoritesUseCase: ObserveFavoritesUseCase,
    private val addFavoriteUseCase: AddFavoriteUseCase,
    private val removeFavoriteUseCase: RemoveFavoriteUseCase,
    private val isFavoriteUseCase: IsFavoriteUseCase,
    private val searchUnifiedUseCase: SearchUnifiedUseCase,
    private val getTopGenresUseCase: GetTopGenresUseCase,
    private val getCategoriesForTypeUseCase: GetCategoriesForTypeUseCase,
    private val advancedCatalogSearchUseCase: AdvancedCatalogSearchUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(FavoritesUiState())
    val state: StateFlow<FavoritesUiState> = _state.asStateFlow()

    // Phase 37 : annule la recherche précédente à chaque frappe, pour éviter
    // qu'un résultat obsolète (requête lente) n'écrase un résultat plus récent.
    private var searchJob: Job? = null
    private var countJob: Job? = null

    init {
        // Phase 41 : Room ré-émet automatiquement après addFavorite/removeFavorite
        // et suit le profil actif (voir FavoritesRepositoryImpl.observeFavorites),
        // plus besoin de reload manuel ni d'écoute séparée du changement de profil.
        viewModelScope.launch {
            observeFavoritesUseCase().collect { favs ->
                _state.update { it.copy(favorites = favs, isLoadingFavorites = false) }
            }
        }
        viewModelScope.launch {
            val topGenres = try {
                getTopGenresUseCase()
            } catch (e: Exception) {
                emptyList()
            }
            _state.update { it.copy(availableGenres = topGenres) }
        }
    }

    fun toggleFavorite(id: Int, type: String, name: String, cover: String?, categoryId: String) {
        viewModelScope.launch {
            val favExists = isFavoriteUseCase(id, type)
            if (favExists) {
                removeFavoriteUseCase(id, type)
            } else {
                addFavoriteUseCase(FavoriteItem(id, type, name, cover, categoryId))
            }
        }
    }

    fun isFavoriteItem(id: Int, type: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            onResult(isFavoriteUseCase(id, type))
        }
    }

    fun onSearchQueryChanged(query: String) {
        _state.update { it.copy(searchQuery = query) }
        performSearch(query)
    }

    fun setFilterSheetOpen(isOpen: Boolean) {
        _state.update { it.copy(isFilterSheetOpen = isOpen) }
        if (isOpen) {
            triggerRealtimeCount()
        }
    }

    fun setMediaType(mediaType: SearchMediaType?) {
        _state.update { current ->
            val newFilter = current.advancedFilter.withMediaType(mediaType)
            current.copy(advancedFilter = newFilter)
        }
        loadCategoriesForSelectedType()
        triggerRealtimeCount()
    }

    private fun loadCategoriesForSelectedType() {
        viewModelScope.launch {
            val mediaType = _state.value.advancedFilter.mediaType
            val categories = try {
                getCategoriesForTypeUseCase(mediaType)
            } catch (e: Exception) {
                emptyList()
            }
            _state.update { it.copy(availableCategories = categories) }
        }
    }

    fun setCategory(categoryId: String?) {
        _state.update { current ->
            current.copy(advancedFilter = current.advancedFilter.copy(categoryId = categoryId))
        }
        triggerRealtimeCount()
    }

    fun setMinRating(rating: Int?) {
        _state.update { current ->
            current.copy(advancedFilter = current.advancedFilter.copy(minRating = rating))
        }
        triggerRealtimeCount()
    }

    fun setYearRange(range: IntRange?) {
        _state.update { current ->
            current.copy(advancedFilter = current.advancedFilter.copy(yearRange = range))
        }
        triggerRealtimeCount()
    }

    fun toggleGenre(genre: String) {
        _state.update { current ->
            val updatedGenres = if (genre in current.advancedFilter.genres) {
                current.advancedFilter.genres - genre
            } else {
                current.advancedFilter.genres + genre
            }
            current.copy(advancedFilter = current.advancedFilter.copy(genres = updatedGenres))
        }
        triggerRealtimeCount()
    }

    fun resetFilter() {
        _state.update { current ->
            current.copy(
                advancedFilter = AdvancedSearchFilter.DEFAULT,
                availableCategories = emptyList()
            )
        }
        triggerRealtimeCount()
    }

    fun applyFilter() {
        _state.update { it.copy(isFilterSheetOpen = false) }
        performSearch(_state.value.searchQuery)
    }

    fun removeMediaTypeFilter() {
        _state.update { current ->
            val newFilter = current.advancedFilter.copy(mediaType = null, categoryId = null)
            current.copy(advancedFilter = newFilter, availableCategories = emptyList())
        }
        triggerRealtimeCount()
        performSearch(_state.value.searchQuery)
    }

    fun removeCategoryFilter() {
        _state.update { current ->
            val newFilter = current.advancedFilter.copy(categoryId = null)
            current.copy(advancedFilter = newFilter)
        }
        triggerRealtimeCount()
        performSearch(_state.value.searchQuery)
    }

    fun removeMinRatingFilter() {
        _state.update { current ->
            val newFilter = current.advancedFilter.copy(minRating = null)
            current.copy(advancedFilter = newFilter)
        }
        triggerRealtimeCount()
        performSearch(_state.value.searchQuery)
    }

    fun removeYearRangeFilter() {
        _state.update { current ->
            val newFilter = current.advancedFilter.copy(
                yearRange = AdvancedSearchFilter.DEFAULT_MIN_YEAR..AdvancedSearchFilter.DEFAULT_MAX_YEAR
            )
            current.copy(advancedFilter = newFilter)
        }
        triggerRealtimeCount()
        performSearch(_state.value.searchQuery)
    }

    fun removeGenreFilter(genre: String) {
        _state.update { current ->
            val newFilter = current.advancedFilter.copy(
                genres = current.advancedFilter.genres - genre
            )
            current.copy(advancedFilter = newFilter)
        }
        triggerRealtimeCount()
        performSearch(_state.value.searchQuery)
    }

    private fun triggerRealtimeCount() {
        countJob?.cancel()
        countJob = viewModelScope.launch {
            delay(150L) // léger debounce
            val query = _state.value.searchQuery
            val filter = _state.value.advancedFilter
            val result = try {
                advancedCatalogSearchUseCase(query, filter)
            } catch (e: Exception) {
                SearchResult()
            }
            val totalCount = result.vodResults.size + result.seriesResults.size
            _state.update { it.copy(filteredResultCount = totalCount) }
        }
    }

    private fun performSearch(query: String) {
        searchJob?.cancel()
        val filter = _state.value.advancedFilter
        if (query.trim().isBlank() && filter.isEmpty) {
            _state.update { it.copy(searchResult = SearchResult(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            _state.update { it.copy(isSearching = true) }
            delay(SEARCH_DEBOUNCE_MILLIS)
            val result = try {
                if (filter.isActive || query.isEmpty()) {
                    advancedCatalogSearchUseCase(query, filter)
                } else {
                    searchUnifiedUseCase(query)
                }
            } catch (e: Exception) {
                SearchResult()
            }
            _state.update { it.copy(searchResult = result, isSearching = false) }
        }
    }
}
