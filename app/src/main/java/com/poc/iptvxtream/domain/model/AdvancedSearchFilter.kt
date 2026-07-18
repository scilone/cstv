package com.poc.iptvxtream.domain.model

data class AdvancedSearchFilter(
    val mediaType: SearchMediaType?,
    val categoryId: String?,
    val minRating: Int?,
    val yearRange: IntRange?,
    val genres: Set<String>
) {
    val isEmpty: Boolean
        get() = mediaType == null &&
                categoryId == null &&
                minRating == null &&
                (yearRange == null || (yearRange.first == DEFAULT_MIN_YEAR && yearRange.last == DEFAULT_MAX_YEAR)) &&
                genres.isEmpty()

    val isActive: Boolean
        get() = !isEmpty

    /**
     * Changer de mediaType réinitialise la catégorie (car elle est liée au type).
     */
    fun withMediaType(newMediaType: SearchMediaType?): AdvancedSearchFilter {
        return if (this.mediaType != newMediaType) {
            this.copy(mediaType = newMediaType, categoryId = null)
        } else {
            this
        }
    }

    companion object {
        const val DEFAULT_MIN_YEAR = 1980
        const val DEFAULT_MAX_YEAR = 2025

        val DEFAULT = AdvancedSearchFilter(
            mediaType = null,
            categoryId = null,
            minRating = null,
            yearRange = DEFAULT_MIN_YEAR..DEFAULT_MAX_YEAR,
            genres = emptySet()
        )
    }
}
