package com.cstv.app.presentation.vod

/** First focusable media target for VOD, series and Live TV catalogue screens. */
enum class CatalogFocusTarget { RESUME, FAVORITES, FIRST_CATEGORY, GRID_FIRST_CELL }

object CatalogInitialFocusTarget {
    fun of(
        isAllMode: Boolean,
        hasResume: Boolean,
        hasFavorites: Boolean,
        firstNonEmptyCategoryId: String?,
        gridItemCount: Int
    ): CatalogFocusTarget? = when {
        !isAllMode && gridItemCount > 0 -> CatalogFocusTarget.GRID_FIRST_CELL
        !isAllMode -> null
        hasResume -> CatalogFocusTarget.RESUME
        hasFavorites -> CatalogFocusTarget.FAVORITES
        firstNonEmptyCategoryId != null -> CatalogFocusTarget.FIRST_CATEGORY
        else -> null
    }
}
