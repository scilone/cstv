package com.cstv.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleNormalizerTest {

    @Test
    fun test_normalize_stripsBracketedAndPipeEnclosedTags() {
        assertEquals("interstellar", TitleNormalizer.normalize("[FR] Interstellar"))
        assertEquals("interstellar", TitleNormalizer.normalize("|FR| Interstellar"))
        assertEquals("interstellar", TitleNormalizer.normalize("Interstellar [MULTI]"))
        assertEquals("interstellar", TitleNormalizer.normalize("Interstellar (VOSTFR)"))
    }

    @Test
    fun test_normalize_replacesSpecialCharactersWithSpaces() {
        assertEquals("die hard", TitleNormalizer.normalize("Die-Hard"))
        assertEquals("the matrix", TitleNormalizer.normalize("The.Matrix"))
        assertEquals("avatar", TitleNormalizer.normalize("Avatar: The Way of Water").split(" ").first())
    }

    @Test
    fun test_normalize_stripsStandaloneQualityAndLanguageTags() {
        assertEquals("inception", TitleNormalizer.normalize("Inception 1080p MULTI x265"))
        assertEquals("inception", TitleNormalizer.normalize("Inception 4k UHD HDR"))
        assertEquals("inception", TitleNormalizer.normalize("Inception VF HD TrueFrench"))
    }

    @Test
    fun test_normalize_stripsYears() {
        assertEquals("gladiator", TitleNormalizer.normalize("Gladiator 2000"))
        assertEquals("gladiator", TitleNormalizer.normalize("Gladiator (2000)"))
        assertEquals("gladiator", TitleNormalizer.normalize("Gladiator [2000]"))
    }

    @Test
    fun test_normalize_collapsesMultipleSpaces() {
        assertEquals("batman begins", TitleNormalizer.normalize("  Batman    Begins  "))
    }

    @Test
    fun test_normalize_preservesNumericOnlyTitles() {
        // L'année EST le titre : ne doit pas être vidée.
        assertEquals("1917", TitleNormalizer.normalize("1917"))
        assertEquals("2012", TitleNormalizer.normalize("|FR| 2012 1080p MULTI"))
    }
}
