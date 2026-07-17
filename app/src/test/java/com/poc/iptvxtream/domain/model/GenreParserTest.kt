package com.poc.iptvxtream.domain.model

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
    fun parseGenres_placeholders_areExcluded() {
        assertTrue(GenreParser.parseGenres("Inconnu").isEmpty())
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

    // --- distinctGenres ---
    @Test
    fun distinctGenres_flattensDeduplicatesCaseInsensitive_andSorts() {
        val raw = listOf("Action, Thriller", "action", "Comédie", null, "Inconnu")
        assertEquals(listOf("Action", "Comédie", "Thriller"), GenreParser.distinctGenres(raw))
    }

    @Test
    fun distinctGenres_keepsFirstCasingSeen() {
        val raw = listOf("ACTION", "action")
        assertEquals(listOf("ACTION"), GenreParser.distinctGenres(raw))
    }

    @Test
    fun distinctGenres_empty_returnsEmpty() {
        assertTrue(GenreParser.distinctGenres(emptyList()).isEmpty())
    }
}
