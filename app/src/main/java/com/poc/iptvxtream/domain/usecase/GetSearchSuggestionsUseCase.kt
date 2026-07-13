package com.poc.iptvxtream.domain.usecase

import com.poc.iptvxtream.domain.model.SearchSuggestion
import com.poc.iptvxtream.domain.repository.FavoritesRepository
import javax.inject.Inject

class GetSearchSuggestionsUseCase @Inject constructor(
    private val repository: FavoritesRepository
) {
    suspend operator fun invoke(query: String, limit: Int = 10): List<SearchSuggestion> {
        return repository.getSearchSuggestions(query, limit)
    }
}
