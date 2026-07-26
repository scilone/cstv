package com.cstv.app.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le point de bascule est testé aux bornes : c'est lui qui décide de partir ou
 * non en réseau, donc la différence entre un trafic maîtrisé et un panel
 * sollicité en permanence.
 */
class CacheTtlTest {

    private val now = 1_000_000_000L

    @Test
    fun neverSyncedIsAlwaysExpired() {
        assertTrue(CacheTtl.isExpired(0L, CacheTtl.CATALOG_MILLIS, now))
        assertTrue(CacheTtl.isExpired(-1L, CacheTtl.CATALOG_MILLIS, now))
    }

    @Test
    fun exactlyAtTtlIsStillFresh() {
        val lastSuccess = now - CacheTtl.CATALOG_MILLIS
        assertFalse(CacheTtl.isExpired(lastSuccess, CacheTtl.CATALOG_MILLIS, now))
    }

    @Test
    fun oneMillisecondBeforeTtlIsFresh() {
        val lastSuccess = now - CacheTtl.CATALOG_MILLIS + 1
        assertFalse(CacheTtl.isExpired(lastSuccess, CacheTtl.CATALOG_MILLIS, now))
    }

    @Test
    fun oneMillisecondAfterTtlIsExpired() {
        val lastSuccess = now - CacheTtl.CATALOG_MILLIS - 1
        assertTrue(CacheTtl.isExpired(lastSuccess, CacheTtl.CATALOG_MILLIS, now))
    }

    @Test
    fun detailsTtlIsSevenDaysAndEpgFifteenMinutes() {
        assertFalse(CacheTtl.isExpired(now - CacheTtl.DETAILS_MILLIS, CacheTtl.DETAILS_MILLIS, now))
        assertTrue(CacheTtl.isExpired(now - CacheTtl.DETAILS_MILLIS - 1, CacheTtl.DETAILS_MILLIS, now))
        assertFalse(CacheTtl.isExpired(now - CacheTtl.EPG_MILLIS, CacheTtl.EPG_MILLIS, now))
        assertTrue(CacheTtl.isExpired(now - CacheTtl.EPG_MILLIS - 1, CacheTtl.EPG_MILLIS, now))
    }
}
