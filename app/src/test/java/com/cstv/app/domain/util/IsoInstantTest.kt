package com.cstv.app.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IsoInstantTest {

    @Test
    fun `parses a UTC offset exactly like DateTimeImmutable ATOM`() {
        // 2024-01-01T00:00:00+00:00 == epoch 1704067200000
        assertEquals(1_704_067_200_000L, IsoInstant.parseMillis("2024-01-01T00:00:00+00:00"))
    }

    @Test
    fun `parses a positive offset by subtracting it from UTC`() {
        // 2024-01-01T02:00:00+02:00 is the same instant as 2024-01-01T00:00:00Z
        assertEquals(1_704_067_200_000L, IsoInstant.parseMillis("2024-01-01T02:00:00+02:00"))
    }

    @Test
    fun `parses a negative offset by adding it back to UTC`() {
        // 2023-12-31T19:00:00-05:00 is the same instant as 2024-01-01T00:00:00Z
        assertEquals(1_704_067_200_000L, IsoInstant.parseMillis("2023-12-31T19:00:00-05:00"))
    }

    @Test
    fun `treats a bare Z suffix as UTC`() {
        assertEquals(1_704_067_200_000L, IsoInstant.parseMillis("2024-01-01T00:00:00Z"))
    }

    @Test
    fun `returns null for null, blank, or malformed input instead of throwing`() {
        assertNull(IsoInstant.parseMillis(null))
        assertNull(IsoInstant.parseMillis(""))
        assertNull(IsoInstant.parseMillis("not-a-date"))
        assertNull(IsoInstant.parseMillis("2024-01-01"))
    }
}
