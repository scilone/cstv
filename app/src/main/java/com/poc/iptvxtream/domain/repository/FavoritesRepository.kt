package com.poc.iptvxtream.domain.repository

import com.poc.iptvxtream.domain.model.FavoriteItem
import com.poc.iptvxtream.domain.model.SearchResult
import kotlinx.coroutines.flow.Flow

interface FavoritesRepository {
    // Phase 41 : ré-émet automatiquement après addFavorite/removeFavorite et
    // suit le profil actif, sans reload manuel depuis les ViewModels.
    fun observeFavorites(): Flow<List<FavoriteItem>>
    suspend fun isFavorite(id: Int, type: String): Boolean
    suspend fun addFavorite(favorite: FavoriteItem)
    suspend fun removeFavorite(id: Int, type: String)
    suspend fun searchUnified(query: String, genre: String? = null): SearchResult

    /** Liste des genres distincts (VOD + Séries) présents dans le cache local. */
    suspend fun getAvailableGenres(): List<String>
}
