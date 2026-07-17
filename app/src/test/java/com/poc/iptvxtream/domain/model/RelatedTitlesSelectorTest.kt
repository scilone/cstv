package com.poc.iptvxtream.domain.model

import org.junit.Assert.*
import org.junit.Test

class RelatedTitlesSelectorTest {

    private fun cand(id: Int, genres: List<String>, rating: Double = 0.0, added: Long = 0L) =
        RelatedTitlesSelector.Candidate(id, genres, rating, added)

    @Test
    fun ordersBySharedGenreCountDescending() {
        val current = listOf("Action", "Thriller", "Drame")
        val candidates = listOf(
            cand(1, listOf("Action")),                       // 1 commun
            cand(2, listOf("Action", "Thriller", "Drame")),  // 3 communs
            cand(3, listOf("Action", "Thriller"))            // 2 communs
        )
        assertEquals(listOf(2, 3, 1), RelatedTitlesSelector.select(current, candidates, 10))
    }

    @Test
    fun excludesCandidatesWithNoSharedGenre() {
        val current = listOf("Action")
        val candidates = listOf(
            cand(1, listOf("Romance", "Drame")),
            cand(2, listOf("Action"))
        )
        assertEquals(listOf(2), RelatedTitlesSelector.select(current, candidates, 10))
    }

    @Test
    fun matchingIsCaseAndWhitespaceInsensitive() {
        val current = listOf("Action")
        val candidates = listOf(cand(1, listOf(" action ")))
        assertEquals(listOf(1), RelatedTitlesSelector.select(current, candidates, 10))
    }

    @Test
    fun respectsLimit() {
        val current = listOf("Action")
        val candidates = (1..20).map { cand(it, listOf("Action"), rating = it.toDouble() / 2) }
        assertEquals(10, RelatedTitlesSelector.select(current, candidates, 10).size)
    }

    @Test
    fun tieBreakPrefersHigherRating() {
        val current = listOf("Action")
        val candidates = listOf(
            cand(1, listOf("Action"), rating = 5.0, added = 100L),
            cand(2, listOf("Action"), rating = 9.0, added = 100L)
        )
        // Même nombre de genres communs, même date d'ajout -> la meilleure note gagne.
        assertEquals(listOf(2, 1), RelatedTitlesSelector.select(current, candidates, 10))
    }

    @Test
    fun tieBreakPrefersMoreRecentWhenRatingEqual() {
        val current = listOf("Action")
        val candidates = listOf(
            cand(1, listOf("Action"), rating = 7.0, added = 100L),
            cand(2, listOf("Action"), rating = 7.0, added = 500L)
        )
        // Note égale -> le plus récemment ajouté (added plus grand) passe devant.
        assertEquals(listOf(2, 1), RelatedTitlesSelector.select(current, candidates, 10))
    }

    @Test
    fun sharedCountDominatesScore() {
        val current = listOf("Action", "Thriller")
        val candidates = listOf(
            cand(1, listOf("Action", "Thriller"), rating = 1.0, added = 0L), // 2 communs, faible note
            cand(2, listOf("Action"), rating = 10.0, added = 999L)           // 1 commun, forte note
        )
        // Le nombre de genres communs prime sur le score.
        assertEquals(listOf(1, 2), RelatedTitlesSelector.select(current, candidates, 10))
    }

    @Test
    fun emptyCurrentGenres_returnsEmpty() {
        assertTrue(RelatedTitlesSelector.select(emptyList(), listOf(cand(1, listOf("Action"))), 10).isEmpty())
    }

    @Test
    fun emptyCandidates_returnsEmpty() {
        assertTrue(RelatedTitlesSelector.select(listOf("Action"), emptyList<RelatedTitlesSelector.Candidate<Int>>(), 10).isEmpty())
    }

    @Test
    fun zeroLimit_returnsEmpty() {
        assertTrue(RelatedTitlesSelector.select(listOf("Action"), listOf(cand(1, listOf("Action"))), 0).isEmpty())
    }
}
