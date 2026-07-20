package com.cstv.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RecommendationEngineTest {

    private fun mockMovie(id: String, genres: String, categoryId: String, rating: String?, added: String?): RecommendationEngine.RecommendableItem {
        return object : RecommendationEngine.RecommendableItem {
            override val uniqueId = id
            override val genres = genres
            override val categoryId = categoryId
            override val rating = rating
            override val addedEpoch = added
            override val releaseYear = null
        }
    }

    @Test
    fun test_buildProfileTaste_calculatesWeightsCorrectly() {
        val watched = listOf(
            mockMovie("1", "Action, Western", "cat1", null, null),
            mockMovie("2", "Western", "cat1", null, null),
            mockMovie("3", "Comédie", "cat2", null, null)
        )

        val taste = RecommendationEngine.buildProfileTaste(watched)

        // Category weights: cat1 (2/3 = 0.66), cat2 (1/3 = 0.33)
        assertEquals(0.666, taste.categoryWeights["cat1"] ?: 0.0, 0.01)
        assertEquals(0.333, taste.categoryWeights["cat2"] ?: 0.0, 0.01)

        // Genre weights: total items with genres = 3
        // Western appears in 2 items -> 2/3 = 0.66
        // Action appears in 1 item -> 1/3 = 0.33
        // Comédie appears in 1 item -> 1/3 = 0.33
        assertEquals(0.666, taste.genreWeights["western"] ?: 0.0, 0.01)
        assertEquals(0.333, taste.genreWeights["action"] ?: 0.0, 0.01)
        assertEquals(0.333, taste.genreWeights["comédie"] ?: 0.0, 0.01)
    }

    @Test
    fun test_scoreCandidate_respectsWeights() {
        val taste = RecommendationEngine.ProfileTaste(
            genreWeights = mapOf("western" to 0.8), // Strongly likes westerns
            categoryWeights = mapOf("cat_west" to 0.5) // Somewhat likes this category
        )

        val currentTime = 1000000L * 1000L // arbitrary current time
        val recentAdded = "1000000" // Added exactly now (freshness = 1.0)
        val oldAdded = "0" // Added a long time ago (freshness = 0.0)

        val candidateWestern = mockMovie("10", "Western", "cat_west", "10.0", recentAdded)
        val candidateHorror = mockMovie("11", "Horreur", "cat_other", "5.0", oldAdded)

        val scoreWestern = RecommendationEngine.scoreCandidate(candidateWestern, taste, currentTime)
        val scoreHorror = RecommendationEngine.scoreCandidate(candidateHorror, taste, currentTime)

        // Western should score extremely high: (0.8 * 0.40) + (0.5 * 0.20) + (1.0 * 0.25) + (1.0 * 0.15) = 0.32 + 0.10 + 0.25 + 0.15 = 0.82
        assertEquals(0.82, scoreWestern, 0.01)

        // Horror should score extremely low: (0.0 * 0.40) + (0.0 * 0.20) + (0.5 * 0.25) + (0.0 * 0.15) = 0.125
        assertEquals(0.125, scoreHorror, 0.01)

        assertTrue(scoreWestern > scoreHorror)
    }

    @Test
    fun test_getTopRecommendations_excludesWatchedItems_andSortsProperly() {
        val taste = RecommendationEngine.ProfileTaste(
            genreWeights = mapOf("action" to 1.0),
            categoryWeights = mapOf("cat1" to 1.0)
        )

        val candidates = listOf(
            mockMovie("1", "Action", "cat1", "9.0", "100"), // Watched!
            mockMovie("2", "Action", "cat1", "8.0", "100"), // High score
            mockMovie("3", "Comédie", "cat2", "2.0", "0")    // Low score
        )

        val results = RecommendationEngine.getTopRecommendations(
            candidates = candidates,
            taste = taste,
            currentTimeMs = 100L * 1000L,
            excludeIds = setOf("1")
        )

        // ID 1 must be excluded
        assertEquals(2, results.size)
        
        // ID 2 should be first because Action matches taste
        assertEquals("2", results[0].uniqueId)
        assertEquals("3", results[1].uniqueId)
    }

    @Test
    fun test_getTopRecommendations_stableSortingForEqualScores() {
        // Same taste, same genres, same category = same base score.
        // The sorting must fallback to Rating (higher first), then Added (fresher first).
        val taste = RecommendationEngine.ProfileTaste(
            genreWeights = mapOf("action" to 1.0),
            categoryWeights = mapOf("cat1" to 1.0)
        )

        val candidates = listOf(
            mockMovie("1", "Action", "cat1", "7.0", "100"), // Lower rating
            mockMovie("2", "Action", "cat1", "9.0", "50"),  // Higher rating, older
            mockMovie("3", "Action", "cat1", "9.0", "150")  // Higher rating, fresher
        )

        val results = RecommendationEngine.getTopRecommendations(
            candidates = candidates,
            taste = taste,
            currentTimeMs = 200L * 1000L,
            excludeIds = emptySet()
        )

        // Expected order: 3 (Rating 9, fresher), 2 (Rating 9, older), 1 (Rating 7)
        assertEquals("3", results[0].uniqueId)
        assertEquals("2", results[1].uniqueId)
        assertEquals("1", results[2].uniqueId)
    }
}
