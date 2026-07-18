package com.cstv.app.domain.model

import org.junit.Assert.*
import org.junit.Test

class GenreParserTest {

    // --- parseGenres ---
    @Test
    fun parseGenres_null_returnsEmpty() {
        assertTrue(GenreParser.parseGenres(null).isEmpty())
    }

    @Test
    fun parseGenres_blank_returnsEmpty() {
        assertTrue(GenreParser.parseGenres("   ").isEmpty())
    }

    @Test
    fun parseGenres_single_returnsOne() {
        assertEquals(listOf("Action"), GenreParser.parseGenres("Action"))
    }

    @Test
    fun parseGenres_multiple_splitsAndTrims() {
        assertEquals(
            listOf("Action", "Thriller", "Science-Fiction"),
            GenreParser.parseGenres("Action ,  Thriller,Science-Fiction")
        )
    }

    @Test
    fun parseGenres_splitsOnSlash() {
        assertEquals(listOf("Action", "Aventure"), GenreParser.parseGenres("Action/Aventure"))
    }

    @Test
    fun parseGenres_splitsOnMixedCommaAndSlash() {
        assertEquals(
            listOf("Action", "Aventure", "Comédie"),
            GenreParser.parseGenres("Action/Aventure, Comédie")
        )
    }

    @Test
    fun parseGenres_placeholders_areExcluded() {
        assertTrue(GenreParser.parseGenres("Inconnu").isEmpty())
        // "N/A" reconnu comme placeholder complet malgré le slash séparateur.
        assertTrue(GenreParser.parseGenres("N/A").isEmpty())
        // Mélange placeholder + vrai genre : seul le vrai genre survit.
        assertEquals(listOf("Action"), GenreParser.parseGenres("Inconnu, Action"))
    }

    @Test
    fun parseGenres_emptyTokens_areDropped() {
        assertEquals(listOf("Action", "Drame"), GenreParser.parseGenres("Action, , Drame,"))
    }

    // --- matches ---
    @Test
    fun matches_caseInsensitive() {
        assertTrue(GenreParser.matches("Action, Thriller", "action"))
        assertTrue(GenreParser.matches("action, thriller", "Action"))
    }

    @Test
    fun matches_exactTokenNotSubstring() {
        // "War" ne doit pas matcher le token "Warrior".
        assertFalse(GenreParser.matches("Warrior, Drame", "War"))
        assertTrue(GenreParser.matches("War, Drame", "War"))
    }

    @Test
    fun matches_nullOrBlankStored_isFalse() {
        assertFalse(GenreParser.matches(null, "Action"))
        assertFalse(GenreParser.matches("", "Action"))
    }

    @Test
    fun matches_blankSelection_isFalse() {
        assertFalse(GenreParser.matches("Action", "  "))
    }

    // --- sharedGenreCount ---
    @Test
    fun sharedGenreCount_countsCommonGenresCaseInsensitive() {
        assertEquals(2, GenreParser.sharedGenreCount("Action, Thriller, Drame", "action, THRILLER"))
    }

    @Test
    fun sharedGenreCount_slashSeparatedGenres() {
        assertEquals(1, GenreParser.sharedGenreCount("Action/Aventure", "Aventure, Comédie"))
    }

    @Test
    fun sharedGenreCount_noOverlap_isZero() {
        assertEquals(0, GenreParser.sharedGenreCount("Action", "Romance, Drame"))
    }

    @Test
    fun sharedGenreCount_nullOrPlaceholder_isZero() {
        assertEquals(0, GenreParser.sharedGenreCount(null, "Action"))
        assertEquals(0, GenreParser.sharedGenreCount("Inconnu", "Action"))
    }
}
