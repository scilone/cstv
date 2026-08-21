package com.cstv.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalMetadataCoverageTest {

    @Test
    fun `processed is linked plus unresolved`() {
        val byKind = ExternalMetadataCoverageByKind(total = 100, linked = 60, unresolved = 10)
        assertEquals(70, byKind.processed)
    }

    @Test
    fun `pending is total minus processed`() {
        val byKind = ExternalMetadataCoverageByKind(total = 100, linked = 60, unresolved = 10)
        assertEquals(30, byKind.pending)
    }

    @Test
    fun `pending never goes negative on inconsistent counters`() {
        val byKind = ExternalMetadataCoverageByKind(total = 10, linked = 8, unresolved = 8)
        assertEquals(0, byKind.pending)
    }

    @Test
    fun `global coverage aggregates linked, unresolved and pending`() {
        val coverage = ExternalMetadataCoverage(
            total = 100,
            linked = 90,
            unresolved = 5,
            movies = ExternalMetadataCoverageByKind(total = 60, linked = 55, unresolved = 3),
            series = ExternalMetadataCoverageByKind(total = 40, linked = 35, unresolved = 2),
        )
        assertEquals(95, coverage.processed)
        assertEquals(5, coverage.pending)
    }

    @Test
    fun `empty catalog is correctly represented`() {
        val coverage = ExternalMetadataCoverage(
            total = 0,
            linked = 0,
            unresolved = 0,
            movies = ExternalMetadataCoverageByKind(0, 0, 0),
            series = ExternalMetadataCoverageByKind(0, 0, 0),
        )
        assertEquals(0, coverage.processed)
        assertEquals(0, coverage.pending)
    }

    @Test
    fun `coveragePercent is null for empty or negative total`() {
        assertNull(coveragePercent(part = 0, total = 0))
        assertNull(coveragePercent(part = 5, total = -1))
    }

    @Test
    fun `coveragePercent is zero when nothing is covered`() {
        assertEquals(0.0, coveragePercent(part = 0, total = 100)!!, 0.0001)
    }

    @Test
    fun `coveragePercent is exact hundred when fully covered`() {
        assertEquals(100.0, coveragePercent(part = 100, total = 100)!!, 0.0001)
    }

    @Test
    fun `coveragePercent computes an intermediate rate`() {
        assertEquals(97.8, coveragePercent(part = 978, total = 1000)!!, 0.0001)
    }

    @Test
    fun `coveragePercent is coerced within 0 and 100 on inconsistent counters`() {
        assertEquals(100.0, coveragePercent(part = 150, total = 100)!!, 0.0001)
    }
}
