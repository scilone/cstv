package com.cstv.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgeRatingTest {

    @Test
    fun `known values map to their AgeRating`() {
        assertEquals(AgeRating.ALL, AgeRating.fromValueOrNull(0))
        assertEquals(AgeRating.TEN, AgeRating.fromValueOrNull(10))
        assertEquals(AgeRating.TWELVE, AgeRating.fromValueOrNull(12))
        assertEquals(AgeRating.SIXTEEN, AgeRating.fromValueOrNull(16))
        assertEquals(AgeRating.EIGHTEEN, AgeRating.fromValueOrNull(18))
    }

    @Test
    fun `null is never converted to ALL`() {
        assertNull(AgeRating.fromValueOrNull(null))
    }

    @Test
    fun `an unrecognized value is treated as unknown, not ALL`() {
        assertNull(AgeRating.fromValueOrNull(7))
    }
}
