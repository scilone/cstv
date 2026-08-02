package com.cstv.app.domain.model

import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

class ApproximateTitleMatcherTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)


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

    @Test
    fun test_computeSimilarity_isLow_forShortPrefixOfLongerTitle() {
        // "The Hunt" ne doit PAS matcher "The Hunt for Red October" (borne de
        // longueur : 2 mots vs 5 mots).
        val score = ApproximateTitleMatcher.computeSimilarity("The Hunt", "The Hunt for Red October")
        assertTrue(score < 0.8)
    }

    @Test
    fun test_computeSimilarity_isLow_forNonContiguousWords() {
        // "Iron Man" ne doit PAS matcher "Iron Fist Man" (mots non contigus).
        val score = ApproximateTitleMatcher.computeSimilarity("Iron Man", "Iron Fist Man")
        assertTrue(score < 0.8)
    }

    @Test
    fun test_computeSimilarity_stillMatches_contiguousPrefixWithinBound() {
        // Contrôle non-régression : le containment légitime reste à 0.9.
        val score = ApproximateTitleMatcher.computeSimilarity("Dragon Ball Z", "Dragon Ball Z Saga Cell")
        assertTrue(score >= 0.9)
    }
}
