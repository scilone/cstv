package com.cstv.app.presentation.home.components

import com.cstv.app.domain.model.TrendingCatalogItem
import com.cstv.app.domain.model.TrendingTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailerPreviewDwellPolicyTest {

    private val sampleItem = TrendingCatalogItem(
        trendingTitle = TrendingTitle(
            canonicalId = "movie:123",
            title = "Test Movie",
            isMovie = true,
            year = 2026,
            posterUrl = "url"
        )
    )

    @Test
    fun `duration reference is exactly 1500ms`() {
        assertEquals(1500L, TRAILER_PREVIEW_DWELL_MS)
    }

    @Test
    fun `isEligible is false when activeItem is null`() {
        assertFalse(
            TrailerPreviewDwellPolicy.isEligible(
                activeItem = null,
                isScrollInProgress = false,
                lifecycleStarted = true
            )
        )
    }

    @Test
    fun `isEligible is false when scroll is in progress`() {
        assertFalse(
            TrailerPreviewDwellPolicy.isEligible(
                activeItem = sampleItem,
                isScrollInProgress = true,
                lifecycleStarted = true
            )
        )
    }

    @Test
    fun `isEligible is false when lifecycle is stopped`() {
        assertFalse(
            TrailerPreviewDwellPolicy.isEligible(
                activeItem = sampleItem,
                isScrollInProgress = false,
                lifecycleStarted = false
            )
        )
    }

    @Test
    fun `isEligible is true when card is stable, lifecycle started, and item is present`() {
        assertTrue(
            TrailerPreviewDwellPolicy.isEligible(
                activeItem = sampleItem,
                isScrollInProgress = false,
                lifecycleStarted = true
            )
        )
    }
}
