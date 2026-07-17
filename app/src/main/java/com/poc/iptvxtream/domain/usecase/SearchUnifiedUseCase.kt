package com.poc.iptvxtream.domain.usecase

import com.poc.iptvxtream.domain.model.SearchResult
import com.poc.iptvxtream.domain.repository.FavoritesRepository
import javax.inject.Inject

class SearchUnifiedUseCase @Inject constructor(
    private val repository: FavoritesRepository
) {
    suspend operator fun invoke(query: String, genre: String? = null): SearchResult {
        return repository.searchUnified(query, genre)
    }
}
