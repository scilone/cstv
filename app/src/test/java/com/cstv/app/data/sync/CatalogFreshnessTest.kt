package com.cstv.app.data.sync

import com.cstv.app.data.local.dao.CatalogSyncStateDao
import com.cstv.app.data.local.entity.CatalogSection
import com.cstv.app.data.local.entity.CatalogSyncStateEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * `ENRICHMENT` doit l'emporter dès qu'il est postérieur à la section catalogue
 * (B14) : sans cela, un cache d'appariement TMDB écrit avant la fin de
 * l'enrichissement reste valide indéfiniment et fige un mauvais rapprochement.
 */
class CatalogFreshnessTest {

    private val dao: CatalogSyncStateDao = mock()
    private val freshness = CatalogFreshness(dao)

    @Before
    fun setUp() {
        // Aucune section connue par défaut : chaque test ne stub que celles utiles.
    }

    @Test
    fun vodSyncedAt_returnsEnrichmentTimestamp_whenLaterThanVodStreams() = runTest {
        whenever(dao.getSection(CatalogSection.VOD_STREAMS)).thenReturn(section(1_000L))
        whenever(dao.getSection(CatalogSection.ENRICHMENT)).thenReturn(section(2_000L))

        assertEquals(2_000L, freshness.vodSyncedAt())
    }

    @Test
    fun seriesSyncedAt_returnsEnrichmentTimestamp_whenLaterThanSeriesStreams() = runTest {
        whenever(dao.getSection(CatalogSection.SERIES_STREAMS)).thenReturn(section(1_000L))
        whenever(dao.getSection(CatalogSection.ENRICHMENT)).thenReturn(section(2_000L))

        assertEquals(2_000L, freshness.seriesSyncedAt())
    }

    @Test
    fun vodSyncedAt_keepsVodStreamsTimestamp_whenEnrichmentNeverRan() = runTest {
        whenever(dao.getSection(CatalogSection.VOD_STREAMS)).thenReturn(section(1_000L))
        whenever(dao.getSection(CatalogSection.ENRICHMENT)).thenReturn(null)

        assertEquals(1_000L, freshness.vodSyncedAt())
    }

    @Test
    fun vodSyncedAt_keepsVodStreamsTimestamp_whenEnrichmentIsOlder() = runTest {
        whenever(dao.getSection(CatalogSection.VOD_STREAMS)).thenReturn(section(2_000L))
        whenever(dao.getSection(CatalogSection.ENRICHMENT)).thenReturn(section(500L))

        assertEquals(2_000L, freshness.vodSyncedAt())
    }

    @Test
    fun vodSyncedAt_returnsZero_whenSectionUnreadable() = runTest {
        whenever(dao.getSection(CatalogSection.VOD_STREAMS)).thenThrow(RuntimeException("illisible"))
        whenever(dao.getSection(CatalogSection.ENRICHMENT)).thenReturn(null)

        assertEquals(0L, freshness.vodSyncedAt())
    }

    private fun section(lastSuccessAt: Long) = CatalogSyncStateEntity(
        section = "irrelevant",
        accountKey = "key",
        lastSuccessAt = lastSuccessAt
    )
}
