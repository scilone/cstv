package com.cstv.app.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApproximateTitleMatcherTest {

    @Test
    fun similarity_identical_isOne() {
        assertTrue(ApproximateTitleMatcher.similarity("dragon ball z", "dragon ball z") == 1.0)
    }

    @Test
    fun similarity_emptyOperand_isZero() {
        assertTrue(ApproximateTitleMatcher.similarity("", "matrix") == 0.0)
        assertTrue(ApproximateTitleMatcher.similarity("matrix", "") == 0.0)
    }

    @Test
    fun matches_exactAfterNormalization() {
        assertTrue(ApproximateTitleMatcher.matches("the matrix", "the matrix"))
    }

    @Test
    fun matches_oneCharDifference_shortName() {
        // "spider man" vs "spiderman" : 1 espace de diff sur 10 → 0.9 ≥ 0.85.
        assertTrue(ApproximateTitleMatcher.matches("spider man", "spiderman"))
    }

    @Test
    fun matches_rejects_warVsWarrior() {
        // Faux positif classique : préfixe commun mais titres distincts.
        assertFalse(ApproximateTitleMatcher.matches("war", "warrior"))
    }

    @Test
    fun matches_rejects_unrelatedTitles() {
        assertFalse(ApproximateTitleMatcher.matches("inception", "interstellar"))
    }

    @Test
    fun matches_rejects_shortPrefix() {
        assertFalse(ApproximateTitleMatcher.matches("up", "upgrade"))
    }
}
