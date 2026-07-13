package com.poc.iptvxtream.presentation.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poc.iptvxtream.domain.model.FavoriteItem
import com.poc.iptvxtream.domain.model.SearchResult
import com.poc.iptvxtream.domain.model.SearchSuggestion
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

data class FavoritesUiState(
    val favorites: List<FavoriteItem> = emptyList(),
    val searchResult: SearchResult = SearchResult(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val isLoadingFavorites: Boolean = false,
    val suggestions: List<SearchSuggestion> = emptyList()
)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val getFavoritesUseCase: GetFavoritesUseCase,
    private val addFavoriteUseCase: AddFavoriteUseCase,
    private val removeFavoriteUseCase: RemoveFavoriteUseCase,
    private val isFavoriteUseCase: IsFavoriteUseCase,
    private val searchUnifiedUseCase: SearchUnifiedUseCase,
    private val getSearchSuggestionsUseCase: GetSearchSuggestionsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(FavoritesUiState())
    val state: StateFlow<FavoritesUiState> = _state.asStateFlow()

    private var suggestionsJob: Job? = null
    private var searchJob: Job? = null

    init {
        loadFavorites()
    }

    fun loadFavorites() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingFavorites = true) }
            val favs = getFavoritesUseCase()
            _state.update { it.copy(favorites = favs, isLoadingFavorites = false) }
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
            loadFavorites()
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
        loadSuggestions(query)
    }

    fun selectSuggestionTerm(term: String) {
        _state.update { it.copy(searchQuery = term, suggestions = emptyList()) }
        performSearch(term)
    }

    fun clearSuggestions() {
        _state.update { it.copy(suggestions = emptyList()) }
    }

    private fun loadSuggestions(query: String) {
        // Annuler la requête précédente : sans cela, deux frappes rapprochées
        // lancent des requêtes Room concurrentes qui peuvent résoudre dans le
        // désordre et écraser les suggestions fraîches par des périmées.
        suggestionsJob?.cancel()
        if (query.trim().isBlank()) {
            _state.update { it.copy(suggestions = emptyList()) }
            return
        }
        suggestionsJob = viewModelScope.launch {
            delay(SUGGESTION_DEBOUNCE_MS)
            try {
                val suggs = getSearchSuggestionsUseCase(query)
                _state.update { it.copy(suggestions = suggs) }
            } catch (e: Exception) {
                // Ignore suggestion fetching errors
            }
        }
    }

    private fun performSearch(query: String) {
        // Idem : annuler la recherche précédente pour éviter qu'un résultat
        // périmé n'écrase un résultat plus récent (frappes rapprochées).
        searchJob?.cancel()
        if (query.trim().isBlank()) {
            _state.update { it.copy(searchResult = SearchResult(), isSearching = false, suggestions = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _state.update { it.copy(isSearching = true) }
            val result = searchUnifiedUseCase(query)
            _state.update { it.copy(searchResult = result, isSearching = false) }
        }
    }

    companion object {
        private const val SUGGESTION_DEBOUNCE_MS = 200L
        private const val SEARCH_DEBOUNCE_MS = 300L
    }
}
