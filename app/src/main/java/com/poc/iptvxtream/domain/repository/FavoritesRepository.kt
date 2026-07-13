package com.poc.iptvxtream.domain.repository

import com.poc.iptvxtream.domain.model.FavoriteItem
import com.poc.iptvxtream.domain.model.SearchResult
import com.poc.iptvxtream.domain.model.SearchSuggestion

interface FavoritesRepository {
    suspend fun getFavorites(): List<FavoriteItem>
    suspend fun isFavorite(id: Int, type: String): Boolean
    suspend fun addFavorite(favorite: FavoriteItem)
    suspend fun removeFavorite(id: Int, type: String)
    suspend fun searchUnified(query: String): SearchResult
    suspend fun getSearchSuggestions(query: String, limit: Int = 10): List<SearchSuggestion>
}
