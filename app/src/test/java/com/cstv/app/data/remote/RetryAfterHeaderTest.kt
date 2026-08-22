package com.cstv.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** T29 débit §2 : la conversion secondes → millisecondes se fait une seule fois, ici. */
class RetryAfterHeaderTest {

    @Test
    fun `delta-seconds is converted to milliseconds`() {
        assertEquals(45_000L, RetryAfterHeader.parseMillis("45"))
        assertEquals(0L, RetryAfterHeader.parseMillis("0"))
        assertEquals(1_000L, RetryAfterHeader.parseMillis(" 1 "))
    }

    @Test
    fun `an absent, malformed or HTTP-date header yields null rather than a guessed delay`() {
        assertNull(RetryAfterHeader.parseMillis(null))
        assertNull(RetryAfterHeader.parseMillis(""))
        assertNull(RetryAfterHeader.parseMillis("soon"))
        assertNull(RetryAfterHeader.parseMillis("Wed, 21 Oct 2015 07:28:00 GMT"))
        assertNull(RetryAfterHeader.parseMillis("-5"))
    }

    @Test
    fun `an absurd header can never put the hydration queue to sleep for hours`() {
        assertEquals(RetryAfterHeader.MAX_RETRY_AFTER_MILLIS, RetryAfterHeader.parseMillis("999999"))
    }
}
