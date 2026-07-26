package com.cstv.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpisodeLabelTest {

    @Test
    fun format_padsSingleDigitSeasonAndEpisode() {
        assertEquals("S01 E03", EpisodeLabel.format(1, 3))
    }

    @Test
    fun format_keepsNumbersAboveNinetyNineIntact() {
        assertEquals("S10 E128", EpisodeLabel.format(10, 128))
    }

    @Test
    fun format_returnsNullWhenSeasonOrEpisodeIsUnknown() {
        assertNull(EpisodeLabel.format(null, 3))
        assertNull(EpisodeLabel.format(1, null))
        assertNull(EpisodeLabel.format(null, null))
    }
}
