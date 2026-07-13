package com.poc.iptvxtream.presentation.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poc.iptvxtream.domain.model.FavoriteItem
import com.poc.iptvxtream.domain.model.SearchResult
import com.poc.iptvxtream.domain.model.SearchSuggestion
import com.poc.iptvxtream.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
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
        if (query.trim().isBlank()) {
            _state.update { it.copy(suggestions = emptyList()) }
            return
        }
        viewModelScope.launch {
            try {
                val suggs = getSearchSuggestionsUseCase(query)
                _state.update { it.copy(suggestions = suggs) }
            } catch (e: Exception) {
                // Ignore suggestion fetching errors
            }
        }
    }

    private fun performSearch(query: String) {
        if (query.trim().isBlank()) {
            _state.update { it.copy(searchResult = SearchResult(), isSearching = false, suggestions = emptyList()) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSearching = true) }
            val result = searchUnifiedUseCase(query)
            _state.update { it.copy(searchResult = result, isSearching = false) }
        }
    }
}
