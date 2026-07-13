package com.poc.iptvxtream.domain.model

sealed class SearchSuggestion {
    data class Item(
        val id: Int,
        val name: String,
        val type: String, // "live", "movie", or "series"
        val iconOrCover: String?,
        val categoryId: String,
        val underlyingItem: Any
    ) : SearchSuggestion()

    data class Term(
        val term: String
    ) : SearchSuggestion()
}
