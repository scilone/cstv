package com.cstv.app.domain.model

import org.junit.Assert.assertTrue
import org.junit.Test

class ApproximateTitleMatcherTest {

    @Test
    fun test_computeSimilarity_isOne_forExactMatch() {
        val score = ApproximateTitleMatcher.computeSimilarity("Inception", "Inception")
        assertTrue(score >= 1.0)
    }

    @Test
    fun test_computeSimilarity_isHigh_forMatchedLanguagesOrQualityTags() {
        val score = ApproximateTitleMatcher.computeSimilarity("Inception", "[FR] Inception 1080p MULTI x265")
        assertTrue(score >= 0.9)
    }

    @Test
    fun test_computeSimilarity_isHigh_forWordBoundarySubstrings() {
        // TMDB is "Dragon Ball Z", IPTV is "Dragon Ball Z Saga Cell"
        val score = ApproximateTitleMatcher.computeSimilarity("Dragon Ball Z", "Dragon Ball Z Saga Cell")
        assertTrue(score >= 0.9) // Word containment check gives 0.9
    }

    @Test
    fun test_computeSimilarity_isLow_forDifferentTitles() {
        val score = ApproximateTitleMatcher.computeSimilarity("War", "Warrior")
        assertTrue(score < 0.8) // Should not match different words
    }
}
