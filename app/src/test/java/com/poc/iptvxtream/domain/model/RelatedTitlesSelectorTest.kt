package com.poc.iptvxtream.domain.model

import org.junit.Assert.*
import org.junit.Test

class RelatedTitlesSelectorTest {

    private fun cand(id: Int, genres: List<String>, rating: Double = 0.0, added: Long = 0L, categoryId: String? = null) =
        RelatedTitlesSelector.Candidate(id, genres, rating, added, categoryId)

    @Test
    fun ordersBySharedGenreCountDescending() {
        val current = listOf("Action", "Thriller", "Drame")
        val candidates = listOf(
            cand(1, listOf("Action")),                       // 1 commun
            cand(2, listOf("Action", "Thriller", "Drame")),  // 3 communs
            cand(3, listOf("Action", "Thriller"))            // 2 communs
        )
        assertEquals(listOf(2, 3, 1), RelatedTitlesSelector.select(current, null, candidates, 10))
    }

    @Test
    fun excludesCandidatesWithNoSharedGenre() {
        val current = listOf("Action")
        val candidates = listOf(
            cand(1, listOf("Romance", "Drame")),
            cand(2, listOf("Action"))
        )
        assertEquals(listOf(2), RelatedTitlesSelector.select(current, null, candidates, 10))
    }

    @Test
    fun matchingIsCaseAndWhitespaceInsensitive() {
        val current = listOf("Action")
        val candidates = listOf(cand(1, listOf(" action ")))
        assertEquals(listOf(1), RelatedTitlesSelector.select(current, null, candidates, 10))
    }

    @Test
    fun respectsLimit() {
        val current = listOf("Action")
        val candidates = (1..20).map { cand(it, listOf("Action"), rating = it.toDouble() / 2) }
        assertEquals(10, RelatedTitlesSelector.select(current, null, candidates, 10).size)
    }

    @Test
    fun tieBreakPrefersHigherRating() {
        val current = listOf("Action")
        val candidates = listOf(
            cand(1, listOf("Action"), rating = 5.0, added = 100L),
            cand(2, listOf("Action"), rating = 9.0, added = 100L)
        )
        // Même nombre de genres communs, même date d'ajout -> la meilleure note gagne.
        assertEquals(listOf(2, 1), RelatedTitlesSelector.select(current, null, candidates, 10))
    }

    @Test
    fun tieBreakPrefersSameCategoryWhenRatingAndAddedEqual() {
        val current = listOf("Action")
        val candidates = listOf(
            cand(1, listOf("Action"), rating = 7.0, added = 100L, categoryId = "99"),
            cand(2, listOf("Action"), rating = 7.0, added = 100L, categoryId = "5")
        )
        // Note et ajout égaux -> le candidat de même catégorie que le média courant ("5") passe devant.
        assertEquals(listOf(2, 1), RelatedTitlesSelector.select(current, "5", candidates, 10))
    }

    @Test
    fun ratingOutweighsCategory() {
        val current = listOf("Action")
        val candidates = listOf(
            // Même catégorie mais note faible : 0.3 de bonus catégorie ne compense pas l'écart de note.
            cand(1, listOf("Action"), rating = 2.0, added = 0L, categoryId = "5"),
            cand(2, listOf("Action"), rating = 10.0, added = 0L, categoryId = "99")
        )
        assertEquals(listOf(2, 1), RelatedTitlesSelector.select(current, "5", candidates, 10))
    }

    @Test
    fun tieBreakPrefersMoreRecentWhenRatingEqual() {
        val current = listOf("Action")
        val candidates = listOf(
            cand(1, listOf("Action"), rating = 7.0, added = 100L),
            cand(2, listOf("Action"), rating = 7.0, added = 500L)
        )
        // Note égale (et pas de catégorie) -> le plus récemment ajouté passe devant.
        assertEquals(listOf(2, 1), RelatedTitlesSelector.select(current, null, candidates, 10))
    }

    @Test
    fun sharedCountDominatesScore() {
        val current = listOf("Action", "Thriller")
        val candidates = listOf(
            cand(1, listOf("Action", "Thriller"), rating = 1.0, added = 0L), // 2 communs, faible note
            cand(2, listOf("Action"), rating = 10.0, added = 999L, categoryId = "5") // 1 commun, forte note, même cat
        )
        // Le nombre de genres communs prime sur le score de départage.
        assertEquals(listOf(1, 2), RelatedTitlesSelector.select(current, "5", candidates, 10))
    }

    @Test
    fun emptyCurrentGenres_returnsEmpty() {
        assertTrue(RelatedTitlesSelector.select(emptyList(), null, listOf(cand(1, listOf("Action"))), 10).isEmpty())
    }

    @Test
    fun emptyCandidates_returnsEmpty() {
        assertTrue(RelatedTitlesSelector.select(listOf("Action"), null, emptyList<RelatedTitlesSelector.Candidate<Int>>(), 10).isEmpty())
    }

    @Test
    fun zeroLimit_returnsEmpty() {
        assertTrue(RelatedTitlesSelector.select(listOf("Action"), null, listOf(cand(1, listOf("Action"))), 0).isEmpty())
    }
}
