package com.cstv.app.domain.model

import org.junit.Assert.*
import org.junit.Test

class ReleaseYearParserTest {

    @Test
    fun parseYear_null_returnsNull() {
        assertNull(ReleaseYearParser.parseYear(null))
    }

    @Test
    fun parseYear_blank_returnsNull() {
        assertNull(ReleaseYearParser.parseYear(""))
        assertNull(ReleaseYearParser.parseYear("   "))
    }

    @Test
    fun parseYear_placeholder_returnsNull() {
        assertNull(ReleaseYearParser.parseYear("Inconnu"))
        assertNull(ReleaseYearParser.parseYear("N/A"))
    }

    @Test
    fun parseYear_bareYear() {
        assertEquals(1989, ReleaseYearParser.parseYear("1989"))
        assertEquals(2015, ReleaseYearParser.parseYear("2015"))
    }

    @Test
    fun parseYear_isoDate() {
        assertEquals(1989, ReleaseYearParser.parseYear("1989-05-12"))
        assertEquals(2015, ReleaseYearParser.parseYear("2015-03"))
    }

    @Test
    fun parseYear_textualDate() {
        assertEquals(1989, ReleaseYearParser.parseYear("12 May 1989"))
    }

    @Test
    fun parseYear_slashDate_picksYearNotDayMonth() {
        // Jour/mois ne sont pas des années plausibles (pas de préfixe 19/20).
        assertEquals(1989, ReleaseYearParser.parseYear("05/12/1989"))
    }

    @Test
    fun parseYear_firstPlausibleYearWins() {
        assertEquals(1999, ReleaseYearParser.parseYear("1999 (remaster 2020)"))
    }

    @Test
    fun parseYear_noPlausibleYear_returnsNull() {
        // 1850 hors borne 19xx/20xx ; 3000 idem.
        assertNull(ReleaseYearParser.parseYear("1850"))
        assertNull(ReleaseYearParser.parseYear("3000"))
        assertNull(ReleaseYearParser.parseYear("42"))
        assertNull(ReleaseYearParser.parseYear("abc"))
    }

    @Test
    fun parseYear_boundaries() {
        assertEquals(1900, ReleaseYearParser.parseYear("1900"))
        assertEquals(2099, ReleaseYearParser.parseYear("2099"))
    }
}
