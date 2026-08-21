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

    // F46-R3 : formatCoveragePercent extrait de SettingsScreen.kt pour rester testable en JVM pur.

    @Test
    fun `formatCoveragePercent renders an exact zero without decimal`() {
        assertEquals("0 %", formatCoveragePercent(0.0))
    }

    @Test
    fun `formatCoveragePercent renders an exact hundred without decimal`() {
        assertEquals("100 %", formatCoveragePercent(100.0))
    }

    @Test
    fun `formatCoveragePercent renders one decimal with a french comma`() {
        assertEquals("97,8 %", formatCoveragePercent(97.8))
    }

    @Test
    fun `formatCoveragePercent rounds to one decimal`() {
        assertEquals("93,8 %", formatCoveragePercent(93.75))
    }

    // F46-R3 / Tâche 7 §10 : scénarios de présentation déclarés dans les notes de développement,
    // couverts ici au niveau domaine faute de harnais de test Compose pour SettingsScreen.

    @Test
    fun `scenario - 100 percent linked and 100 percent processed`() {
        assertEquals("100 %", formatCoveragePercent(coveragePercent(part = 100, total = 100)!!))
    }

    @Test
    fun `scenario - 80 percent linked and 100 percent processed`() {
        assertEquals("80 %", formatCoveragePercent(coveragePercent(part = 80, total = 100)!!))
        assertEquals("100 %", formatCoveragePercent(coveragePercent(part = 100, total = 100)!!))
    }

    @Test
    fun `scenario - 80 percent linked and 90 percent processed`() {
        assertEquals("80 %", formatCoveragePercent(coveragePercent(part = 80, total = 100)!!))
        assertEquals("90 %", formatCoveragePercent(coveragePercent(part = 90, total = 100)!!))
    }

    @Test
    fun `scenario - movies at 100 percent and series at 50 percent`() {
        val movies = ExternalMetadataCoverageByKind(total = 40, linked = 40, unresolved = 0)
        val series = ExternalMetadataCoverageByKind(total = 40, linked = 20, unresolved = 0)
        assertEquals("100 %", formatCoveragePercent(coveragePercent(movies.linked, movies.total)!!))
        assertEquals("50 %", formatCoveragePercent(coveragePercent(series.linked, series.total)!!))
    }

    @Test
    fun `scenario - series type absent yields no percent`() {
        val series = ExternalMetadataCoverageByKind(total = 0, linked = 0, unresolved = 0)
        assertNull(coveragePercent(series.linked, series.total))
    }

    @Test
    fun `scenario - empty catalog yields no percent`() {
        assertNull(coveragePercent(part = 0, total = 0))
    }
}
