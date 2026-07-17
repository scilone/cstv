package com.poc.iptvxtream.presentation.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poc.iptvxtream.domain.model.FavoriteItem
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
    val availableGenres: List<String> = emptyList(),
    val selectedGenre: String? = null
)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val observeFavoritesUseCase: ObserveFavoritesUseCase,
    private val addFavoriteUseCase: AddFavoriteUseCase,
    private val removeFavoriteUseCase: RemoveFavoriteUseCase,
    private val isFavoriteUseCase: IsFavoriteUseCase,
    private val searchUnifiedUseCase: SearchUnifiedUseCase,
    private val getAvailableGenresUseCase: GetAvailableGenresUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(FavoritesUiState())
    val state: StateFlow<FavoritesUiState> = _state.asStateFlow()

    // Phase 37 : annule la recherche précédente à chaque frappe, pour éviter
    // qu'un résultat obsolète (requête lente) n'écrase un résultat plus récent.
    private var searchJob: Job? = null

    init {
        // Phase 41 : Room ré-émet automatiquement après addFavorite/removeFavorite
        // et suit le profil actif (voir FavoritesRepositoryImpl.observeFavorites),
        // plus besoin de reload manuel ni d'écoute séparée du changement de profil.
        viewModelScope.launch {
            observeFavoritesUseCase().collect { favs ->
                _state.update { it.copy(favorites = favs, isLoadingFavorites = false) }
            }
        }
        refreshGenres()
    }

    /**
     * Recharge la liste des genres disponibles depuis le cache local. Appelée à
     * l'init et à l'ouverture de l'écran de recherche : la liste se complète au
     * fil de l'enrichissement en arrière-plan des détails d'items.
     */
    fun refreshGenres() {
        viewModelScope.launch {
            val genres = getAvailableGenresUseCase()
            _state.update { current ->
                // Si le genre sélectionné a disparu du catalogue, on le désélectionne.
                val selection = current.selectedGenre?.takeIf { sel -> genres.any { it.equals(sel, ignoreCase = true) } }
                current.copy(availableGenres = genres, selectedGenre = selection)
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
        _state.update { it.copy(searchQuery = query) }
        performSearch()
    }

    /** Sélectionne un genre (ou le désélectionne si déjà actif) puis relance la recherche. */
    fun onGenreSelected(genre: String) {
        _state.update { it.copy(selectedGenre = if (it.selectedGenre == genre) null else genre) }
        performSearch()
    }

    private fun performSearch() {
        searchJob?.cancel()
        val query = _state.value.searchQuery
        val genre = _state.value.selectedGenre
        if (query.trim().isBlank() && genre == null) {
            _state.update { it.copy(searchResult = SearchResult(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            _state.update { it.copy(isSearching = true) }
            delay(SEARCH_DEBOUNCE_MILLIS)
            val result = searchUnifiedUseCase(query, genre)
            _state.update { it.copy(searchResult = result, isSearching = false) }
        }
    }
}
