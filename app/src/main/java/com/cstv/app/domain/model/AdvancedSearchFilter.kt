package com.cstv.app.domain.model

/**
 * [yearRange] = `null` signifie "pas de filtre année" (comme [categoryId] ou
 * [minRating]), et non une plage par défaut fixe : les bornes réelles
 * (min/max du catalogue) sont dynamiques et calculées par
 * [com.cstv.app.domain.usecase.GetCatalogYearRangeUseCase], pas codées
 * en dur ici.
 */
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
                yearRange == null &&
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
        val DEFAULT = AdvancedSearchFilter(
            mediaType = null,
            categoryId = null,
            minRating = null,
            yearRange = null,
            genres = emptySet()
        )
    }
}
