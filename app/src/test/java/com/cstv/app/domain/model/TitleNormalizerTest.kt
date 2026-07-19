package com.cstv.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleNormalizerTest {

    @Test
    fun normalize_null_returnsEmpty() {
        assertEquals("", TitleNormalizer.normalize(null))
    }

    @Test
    fun normalize_blank_returnsEmpty() {
        assertEquals("", TitleNormalizer.normalize("   "))
    }

    @Test
    fun normalize_clean_title_lowercasesAndTrims() {
        assertEquals("dragon ball z", TitleNormalizer.normalize("  Dragon Ball Z  "))
    }

    @Test
    fun normalize_stripsPipesAndLanguageAndQuality() {
        assertEquals(
            "dragon ball z",
            TitleNormalizer.normalize("|FR| Dragon Ball Z 1080p MULTI")
        )
    }

    @Test
    fun normalize_stripsBracketsAndCodec() {
        assertEquals(
            "the matrix",
            TitleNormalizer.normalize("[VOSTFR] The Matrix [x265] HDR")
        )
    }

    @Test
    fun normalize_stripsYearInParens() {
        assertEquals("blade runner", TitleNormalizer.normalize("Blade Runner (1982)"))
    }

    @Test
    fun normalize_keepsBareYearToken() {
        // Année NON entre parenthèses = potentiellement le titre lui-même.
        assertEquals("1917", TitleNormalizer.normalize("1917"))
        assertEquals("2012", TitleNormalizer.normalize("|FR| 2012 1080p"))
    }

    @Test
    fun normalize_collapsesSeparatorsDotsUnderscores() {
        assertEquals(
            "spider man homecoming",
            TitleNormalizer.normalize("Spider.Man_Homecoming")
        )
    }

    @Test
    fun normalize_allTags_returnsEmpty() {
        assertEquals("", TitleNormalizer.normalize("|FR| 1080p MULTI x265"))
    }

    @Test
    fun normalize_keepsAccentsLowercased() {
        assertEquals("amélie", TitleNormalizer.normalize("Amélie"))
    }
}
