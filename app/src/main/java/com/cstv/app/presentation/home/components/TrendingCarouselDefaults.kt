package com.cstv.app.presentation.home.components

import com.cstv.app.domain.model.TrendingCatalogItem

/**
 * Shared constants and defaults for the trending carousels (mobile and TV).
 *
 * Product decision: The trailer preview should only load after a stable dwell time
 * of 1.5 seconds on the active card.
 */
internal const val TRAILER_PREVIEW_DWELL_MS = 1_500L

/**
 * Pure policy class to evaluate eligibility of trailer previews.
 * This makes the logic testable on JVM without Compose dependencies.
 */
object TrailerPreviewDwellPolicy {
    /**
     * Determines whether a trending item is eligible for preview based on stability conditions.
     */
    fun isEligible(
        activeItem: TrendingCatalogItem?,
        isScrollInProgress: Boolean,
        lifecycleStarted: Boolean
    ): Boolean {
        return activeItem != null && !isScrollInProgress && lifecycleStarted
    }
}
