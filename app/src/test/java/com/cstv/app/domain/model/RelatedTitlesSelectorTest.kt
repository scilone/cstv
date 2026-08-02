package com.cstv.app.domain.model

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

class RelatedTitlesSelectorTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)


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
    fun sameCategoryCountsAsOneExtraGenre() {
        val current = listOf("Action")
        val candidates = listOf(
            cand(1, listOf("Action"), rating = 9.0, added = 0L, categoryId = "99"), // rang 1
            cand(2, listOf("Action"), rating = 2.0, added = 0L, categoryId = "5")   // rang 2 (même cat), malgré note faible
        )
        // Même catégorie que le média courant ("5") -> rang supérieur, prime sur la note.
        assertEquals(listOf(2, 1), RelatedTitlesSelector.select(current, "5", candidates, 10))
    }

    @Test
    fun categoryBonusTiesWithAnExtraCommonGenre() {
        val current = listOf("Action", "Thriller")
        val candidates = listOf(
            // 2 genres communs, catégorie différente -> rang 2.
            cand(1, listOf("Action", "Thriller"), rating = 1.0, added = 0L, categoryId = "99"),
            // 1 genre commun + même catégorie -> rang 2 aussi ; départage par note.
            cand(2, listOf("Action"), rating = 8.0, added = 0L, categoryId = "5")
        )
        // Rangs égaux (2 = 2) -> la meilleure note départage : candidat 2.
        assertEquals(listOf(2, 1), RelatedTitlesSelector.select(current, "5", candidates, 10))
    }

    @Test
    fun tieBreakPrefersMoreRecentWhenRatingEqual() {
        val current = listOf("Action")
        val candidates = listOf(
            cand(1, listOf("Action"), rating = 7.0, added = 100L),
            cand(2, listOf("Action"), rating = 7.0, added = 500L)
        )
        // Rang et note égaux (pas de catégorie) -> le plus récemment ajouté passe devant.
        assertEquals(listOf(2, 1), RelatedTitlesSelector.select(current, null, candidates, 10))
    }

    @Test
    fun moreCommonGenresStillOutranksCategoryBonus() {
        val current = listOf("Action", "Thriller", "Drame")
        val candidates = listOf(
            // 3 genres communs, catégorie différente -> rang 3.
            cand(1, listOf("Action", "Thriller", "Drame"), rating = 1.0, added = 0L, categoryId = "99"),
            // 1 genre commun + même catégorie -> rang 2.
            cand(2, listOf("Action"), rating = 10.0, added = 999L, categoryId = "5")
        )
        assertEquals(listOf(1, 2), RelatedTitlesSelector.select(current, "5", candidates, 10))
    }

    @Test
    fun penalizesExtraCandidateGenresInAcceptanceCriteriaOrder() {
        val candidates = listOf(
            cand(1, listOf("Drame"), categoryId = "popular"),
            cand(2, listOf("Drame"), categoryId = "other"),
            cand(3, listOf("Drame", "Action"), categoryId = "popular"),
            cand(4, listOf("Drame", "Action", "Thriller"), categoryId = "popular"),
            cand(5, listOf("Action", "Aventure"), categoryId = "popular")
        )

        assertEquals(listOf(1, 3, 4, 2), RelatedTitlesSelector.select(listOf("Drame"), "popular", candidates, 10))
    }

    @Test
    fun extraGenrePenaltyOutranksSecondaryScore() {
        val candidates = listOf(
            cand(1, listOf("Drame"), rating = 1.0, categoryId = "popular"),
            cand(2, listOf("Drame", "Action"), rating = 10.0, categoryId = "popular")
        )

        assertEquals(listOf(1, 2), RelatedTitlesSelector.select(listOf("Drame"), "popular", candidates, 10))
    }

    @Test
    fun extraGenrePenaltyCanBreakCategoryBonusTie() {
        val candidates = listOf(
            cand(1, listOf("Action", "Thriller", "Comédie"), rating = 10.0, categoryId = "other"),
            cand(2, listOf("Action"), rating = 1.0, categoryId = "popular")
        )

        assertEquals(listOf(2, 1), RelatedTitlesSelector.select(listOf("Action", "Thriller"), "popular", candidates, 10))
    }

    @Test
    fun cappedPenaltyCannotOvertakeOneMoreSharedGenre() {
        val manyExtraGenres = (1..40).map { "Extra$it" }
        val candidates = listOf(
            cand(1, listOf("Drame", "Action") + manyExtraGenres),
            cand(2, listOf("Drame"), rating = 10.0)
        )

        assertEquals(listOf(1, 2), RelatedTitlesSelector.select(listOf("Drame", "Action"), null, candidates, 10))
    }

    @Test
    fun heavilyPenalizedCandidateRemainsEligible() {
        val candidates = listOf(cand(1, listOf("Drame") + (1..40).map { "Extra$it" }))

        assertEquals(listOf(1), RelatedTitlesSelector.select(listOf("Drame"), null, candidates, 10))
    }

    @Test
    fun normalizationAndDuplicatesDoNotCreateExtraPenalty() {
        val candidates = listOf(
            cand(1, listOf("Drame"), rating = 1.0),
            cand(2, listOf(" Drame ", "drame", " DRAME "), rating = 10.0)
        )

        assertEquals(listOf(2, 1), RelatedTitlesSelector.select(listOf("drame"), null, candidates, 10))
    }

    @Test
    fun genresOnlyPresentOnCurrentMediaDoNotChangeOrdering() {
        val candidates = listOf(
            cand(1, listOf("Drame"), rating = 1.0),
            cand(2, listOf("Drame", "Romance"), rating = 10.0)
        )

        val orderWithOnlySharedGenre = RelatedTitlesSelector.select(listOf("Drame"), null, candidates, 10)
        val orderWithExtraCurrentGenres = RelatedTitlesSelector.select(listOf("Drame", "Action", "Thriller"), null, candidates, 10)

        assertEquals(listOf(1, 2), orderWithOnlySharedGenre)
        assertEquals(orderWithOnlySharedGenre, orderWithExtraCurrentGenres)
    }

    @Test
    fun blankCurrentGenres_returnEmpty() {
        assertTrue(RelatedTitlesSelector.select(listOf(" ", ""), null, listOf(cand(1, listOf("Drame"))), 10).isEmpty())
    }

    @Test
    fun blankCandidateGenres_areExcluded() {
        val candidates = listOf(cand(1, listOf(" ", "")), cand(2, listOf("Drame")))

        assertEquals(listOf(2), RelatedTitlesSelector.select(listOf("Drame"), null, candidates, 10))
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
