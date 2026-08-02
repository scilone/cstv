package com.cstv.app.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogFilterMatcherTest {
    @Test
    fun missingOrInvalidRating_isExcludedOnlyWhenMinimumIsSet() {
        assertFalse(CatalogFilterMatcher.matchesRating(null, 7))
        assertFalse(CatalogFilterMatcher.matchesRating("not-a-number", 7))
        assertTrue(CatalogFilterMatcher.matchesRating(null, null))
    }

    @Test
    fun ratingAboveThresholdIsIncludedAndBelowIsExcluded() {
        assertTrue(CatalogFilterMatcher.matchesRating("8.0", 7))
        assertTrue(CatalogFilterMatcher.matchesRating("7.0", 7))
        assertFalse(CatalogFilterMatcher.matchesRating("6.9", 7))
    }

    @Test
    fun yearIsOptionalUnlessRangeIsActive() {
        assertTrue(CatalogFilterMatcher.matchesYear(null, null))
        assertFalse(CatalogFilterMatcher.matchesYear(null, 2020..2024))
        assertTrue(CatalogFilterMatcher.matchesYear(2022, 2020..2024))
    }

    @Test
    fun genresUseNormalizedAndSemantics() {
        assertTrue(CatalogFilterMatcher.matchesGenres("Action/Thriller", setOf("action", "THRILLER")))
        assertFalse(CatalogFilterMatcher.matchesGenres("Action", setOf("Action", "Drame")))
    }

    @Test
    fun emptyGenreSetMeansNoFilter() {
        assertTrue(CatalogFilterMatcher.matchesGenres(null, emptySet()))
        assertTrue(CatalogFilterMatcher.matchesGenres("Action", emptySet()))
    }

    @Test
    fun defaultFilterMatchesEveryContent() {
        assertTrue(CatalogFilterMatcher.matchesContent(null, null, null, AdvancedSearchFilter.DEFAULT))
    }
}
