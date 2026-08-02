package com.cstv.app.domain.model

/**
 * Shared, Android-free predicates for the advanced catalogue filters.
 * Keeping them here prevents the TV category filters from drifting from global
 * search semantics.
 */
object CatalogFilterMatcher {
    fun matchesRating(rating: String?, minRating: Int?): Boolean {
        if (minRating == null) return true
        return (rating?.trim()?.toDoubleOrNull() ?: 0.0) >= minRating
    }

    fun matchesYear(releaseYear: Int?, range: IntRange?): Boolean =
        range == null || (releaseYear != null && releaseYear in range)

    fun matchesGenres(genre: String?, selected: Set<String>): Boolean =
        selected.all { GenreParser.matches(genre, it) }

    fun matchesContent(
        rating: String?,
        releaseYear: Int?,
        genre: String?,
        filter: AdvancedSearchFilter
    ): Boolean = matchesRating(rating, filter.minRating) &&
        matchesYear(releaseYear, filter.yearRange) &&
        matchesGenres(genre, filter.genres)
}
