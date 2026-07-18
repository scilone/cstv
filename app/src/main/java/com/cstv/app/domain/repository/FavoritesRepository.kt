package com.cstv.app.domain.repository

import com.cstv.app.domain.model.FavoriteItem
import com.cstv.app.domain.model.SearchResult
import kotlinx.coroutines.flow.Flow

interface FavoritesRepository {
    // Phase 41 : ré-émet automatiquement après addFavorite/removeFavorite et
    // suit le profil actif, sans reload manuel depuis les ViewModels.
    fun observeFavorites(): Flow<List<FavoriteItem>>
    suspend fun isFavorite(id: Int, type: String): Boolean
    suspend fun addFavorite(favorite: FavoriteItem)
    suspend fun removeFavorite(id: Int, type: String)
    suspend fun searchUnified(query: String): SearchResult
}
