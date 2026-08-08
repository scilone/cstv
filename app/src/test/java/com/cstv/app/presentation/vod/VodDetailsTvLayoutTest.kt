package com.cstv.app.presentation.vod

import org.junit.Assert.assertEquals
import org.junit.Test

class VodDetailsTvLayoutTest {

    @Test
    fun test_tvDetailsRelatedShiftPx_relatedRowEmpty_returnsZero() {
        val shift = tvDetailsRelatedShiftPx(
            mainBlockHeight = 400f,
            relatedRowHeight = 0f,
            bottomReserve = 24f,
            screenHeight = 540f
        )
        assertEquals(0f, shift, 0.001f)
    }

    @Test
    fun test_tvDetailsRelatedShiftPx_noOverflow_returnsZero() {
        // Height of elements fits inside the screen with bottom reserve
        val shift = tvDetailsRelatedShiftPx(
            mainBlockHeight = 350f,
            relatedRowHeight = 100f,
            bottomReserve = 24f,
            screenHeight = 540f
        )
        // 350 + 100 + 24 = 474, which is <= 540
        assertEquals(0f, shift, 0.001f)
    }

    @Test
    fun test_tvDetailsRelatedShiftPx_withOverflow_returnsCorrectShift() {
        // Elements exceed screen height
        val shift = tvDetailsRelatedShiftPx(
            mainBlockHeight = 430f,
            relatedRowHeight = 150f,
            bottomReserve = 24f,
            screenHeight = 540f
        )
        // 430 + 150 + 24 = 604
        // 604 - 540 = 64
        assertEquals(64f, shift, 0.001f)
    }

    /**
     * Cas réel de la fiche : le bloc principal occupe exactement
     * `hauteurÉcran - TV_DETAILS_RELATED_PEEK` (110 dp de débord). La remontée
     * doit alors valoir `hauteurRangée + réserve - débord`, c'est-à-dire la
     * part de la rangée réellement hors écran.
     *
     * Ce cas fige l'attendu qui manquait quand la rangée était mesurée à tort
     * à la hauteur du débord (110) : la remontée tombait à la seule réserve.
     */
    @Test
    fun test_tvDetailsRelatedShiftPx_mainBlockIsScreenMinusPeek_shiftsByHiddenPart() {
        val screenHeight = 1080f
        val peek = 110f
        val relatedRowHeight = 200f
        val bottomReserve = 24f

        val shift = tvDetailsRelatedShiftPx(
            mainBlockHeight = screenHeight - peek,
            relatedRowHeight = relatedRowHeight,
            bottomReserve = bottomReserve,
            screenHeight = screenHeight
        )

        // 200 + 24 - 110 = 114
        assertEquals(relatedRowHeight + bottomReserve - peek, shift, 0.001f)
        assertEquals(114f, shift, 0.001f)
    }

    /**
     * Même géométrie, mais avec une rangée aplatie à la hauteur du débord :
     * la remontée se réduit à la réserve basse. Documente la valeur observée
     * lorsque la mesure de la rangée est contrainte par son parent.
     */
    @Test
    fun test_tvDetailsRelatedShiftPx_relatedRowClampedToPeek_shiftsByReserveOnly() {
        val screenHeight = 1080f
        val peek = 110f

        val shift = tvDetailsRelatedShiftPx(
            mainBlockHeight = screenHeight - peek,
            relatedRowHeight = peek,
            bottomReserve = 24f,
            screenHeight = screenHeight
        )

        assertEquals(24f, shift, 0.001f)
    }

    @Test
    fun test_tvDetailsRelatedShiftPx_withNegativeRelatedHeight_returnsZero() {
        val shift = tvDetailsRelatedShiftPx(
            mainBlockHeight = 430f,
            relatedRowHeight = -10f,
            bottomReserve = 24f,
            screenHeight = 540f
        )
        assertEquals(0f, shift, 0.001f)
    }
}
