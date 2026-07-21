package com.cstv.app.presentation.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cstv.app.domain.model.AdvancedSearchFilter
import com.cstv.app.domain.model.CategoryWithCount
import com.cstv.app.domain.model.FavoriteItem
import com.cstv.app.domain.model.SearchMediaType
import com.cstv.app.domain.model.SearchResult
import com.cstv.app.domain.usecase.*
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
    val filteredResultCount: Int = 0,
    // Bornes dynamiques (min/max réels du catalogue) affichées par le slider
    // année tant qu'aucun filtre année explicite n'est choisi (yearRange
    // null). Valeur de repli le temps du chargement initial (voir
    // GetCatalogYearRangeUseCase).
    val catalogYearRange: IntRange = 1980..2025,
    // Passe à true dès que l'utilisateur clique "Voir les résultats" (même
    // sans filtre ni texte saisi) : affiche alors tout le catalogue au lieu
    // de l'invite initiale "Saisissez un mot-clé...".
    val hasBrowsedAll: Boolean = false
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
    private val advancedCatalogSearchUseCase: AdvancedCatalogSearchUseCase,
    private val getCatalogYearRangeUseCase: GetCatalogYearRangeUseCase,
    private val categoryPreferenceRepository: com.cstv.app.domain.repository.CategoryPreferenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(FavoritesUiState())
    val state: StateFlow<FavoritesUiState> = _state.asStateFlow()

    // Phase 37 : annule la recherche précédente à chaque frappe, pour éviter
    // qu'un résultat obsolète (requête lente) n'écrase un résultat plus récent.
    private var searchJob: Job? = null
    private var countJob: Job? = null
    private var isCreditSearchActive = false

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
        viewModelScope.launch {
            val yearRange = try {
                getCatalogYearRangeUseCase()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value.catalogYearRange
            }
            _state.update { it.copy(catalogYearRange = yearRange) }
        }
        viewModelScope.launch {
            categoryPreferenceRepository.changes.collect {
                val currentQuery = _state.value.searchQuery
                val currentFilter = _state.value.advancedFilter
                if (currentQuery.isNotEmpty() || currentFilter.isActive || _state.value.hasBrowsedAll) {
                    performSearch(currentQuery, useAdvancedCatalogSearch = isCreditSearchActive)
                }
            }
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
        isCreditSearchActive = false
        _state.update { it.copy(searchQuery = query) }
        performSearch(query)
    }

    fun searchFromCredit(query: String) {
        searchJob?.cancel()
        countJob?.cancel()
        isCreditSearchActive = true
        _state.update {
            it.copy(
                searchQuery = query,
                searchResult = SearchResult(),
                isSearching = false,
                advancedFilter = AdvancedSearchFilter.DEFAULT,
                isFilterSheetOpen = false,
                availableCategories = emptyList(),
                filteredResultCount = 0,
                hasBrowsedAll = false
            )
        }
        performSearch(query, force = true, useAdvancedCatalogSearch = true)
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
            // Une sélection qui couvre tout le catalogue équivaut à "pas de
            // filtre" : normalisée à null pour rester cohérente avec isEmpty
            // et éviter un chip "actif" qui ne filtre en réalité rien.
            val normalized = range?.takeUnless {
                it.first <= current.catalogYearRange.first && it.last >= current.catalogYearRange.last
            }
            current.copy(advancedFilter = current.advancedFilter.copy(yearRange = normalized))
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
        _state.update { it.copy(isFilterSheetOpen = false, hasBrowsedAll = true) }
        performSearch(_state.value.searchQuery, force = true)
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
            current.copy(advancedFilter = current.advancedFilter.copy(yearRange = null))
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

    /**
     * [force] = true court-circuite le repli "aucune query + aucun filtre =
     * résultat vide" : utilisé par [applyFilter] (clic explicite "Voir les
     * résultats"), où l'absence de filtre doit renvoyer tout le catalogue
     * Films+Séries plutôt que rien.
     */
    private fun performSearch(
        query: String,
        force: Boolean = false,
        useAdvancedCatalogSearch: Boolean = false
    ) {
        searchJob?.cancel()
        val filter = _state.value.advancedFilter
        if (!force && query.trim().isBlank() && filter.isEmpty) {
            _state.update { it.copy(searchResult = SearchResult(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            _state.update { it.copy(isSearching = true) }
            delay(SEARCH_DEBOUNCE_MILLIS)
            val result = try {
                if (useAdvancedCatalogSearch || filter.isActive || query.isEmpty()) {
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
