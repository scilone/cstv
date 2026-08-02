package com.cstv.app.data.sync

import com.cstv.app.data.local.dao.CatalogSyncStateDao
import com.cstv.app.data.local.entity.CatalogSection
import com.cstv.app.data.local.entity.CatalogSyncStateEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * `ENRICHMENT` doit l'emporter dès qu'il est postérieur à la section catalogue
 * (B14) : sans cela, un cache d'appariement TMDB écrit avant la fin de
 * l'enrichissement reste valide indéfiniment et fige un mauvais rapprochement.
 */
class CatalogFreshnessTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)


    private val dao: CatalogSyncStateDao = mock()
    private val freshness = CatalogFreshness(dao)

    @Test
    fun vodSyncedAt_returnsEnrichmentTimestamp_whenLaterThanVodStreams() = runTest {
        whenever(dao.getSection(CatalogSection.VOD_STREAMS)).thenReturn(section(CatalogSection.VOD_STREAMS, 1_000L))
        whenever(dao.getSection(CatalogSection.ENRICHMENT)).thenReturn(section(CatalogSection.ENRICHMENT, 2_000L))

        assertEquals(2_000L, freshness.vodSyncedAt())
    }

    @Test
    fun seriesSyncedAt_returnsEnrichmentTimestamp_whenLaterThanSeriesStreams() = runTest {
        whenever(dao.getSection(CatalogSection.SERIES_STREAMS)).thenReturn(section(CatalogSection.SERIES_STREAMS, 1_000L))
        whenever(dao.getSection(CatalogSection.ENRICHMENT)).thenReturn(section(CatalogSection.ENRICHMENT, 2_000L))

        assertEquals(2_000L, freshness.seriesSyncedAt())
    }

    @Test
    fun vodSyncedAt_keepsVodStreamsTimestamp_whenEnrichmentNeverRan() = runTest {
        whenever(dao.getSection(CatalogSection.VOD_STREAMS)).thenReturn(section(CatalogSection.VOD_STREAMS, 1_000L))
        whenever(dao.getSection(CatalogSection.ENRICHMENT)).thenReturn(null)

        assertEquals(1_000L, freshness.vodSyncedAt())
    }

    @Test
    fun vodSyncedAt_keepsVodStreamsTimestamp_whenEnrichmentIsOlder() = runTest {
        whenever(dao.getSection(CatalogSection.VOD_STREAMS)).thenReturn(section(CatalogSection.VOD_STREAMS, 2_000L))
        whenever(dao.getSection(CatalogSection.ENRICHMENT)).thenReturn(section(CatalogSection.ENRICHMENT, 500L))

        assertEquals(2_000L, freshness.vodSyncedAt())
    }

    @Test
    fun vodSyncedAt_returnsZero_whenSectionUnreadable() = runTest {
        whenever(dao.getSection(CatalogSection.VOD_STREAMS)).thenThrow(RuntimeException("illisible"))
        whenever(dao.getSection(CatalogSection.ENRICHMENT)).thenReturn(null)

        assertEquals(0L, freshness.vodSyncedAt())
    }

    @Test
    fun seriesSyncedAt_keepsSeriesTimestamp_whenEnrichmentIsUnreadable() = runTest {
        whenever(dao.getSection(CatalogSection.SERIES_STREAMS)).thenReturn(section(CatalogSection.SERIES_STREAMS, 1_000L))
        whenever(dao.getSection(CatalogSection.ENRICHMENT)).thenThrow(RuntimeException("illisible"))

        assertEquals(1_000L, freshness.seriesSyncedAt())
    }

    @Test
    fun vodSyncedAt_keepsVodTimestamp_whenEnrichmentIsUnreadable() = runTest {
        whenever(dao.getSection(CatalogSection.VOD_STREAMS)).thenReturn(section(CatalogSection.VOD_STREAMS, 1_000L))
        whenever(dao.getSection(CatalogSection.ENRICHMENT)).thenThrow(RuntimeException("illisible"))

        assertEquals(1_000L, freshness.vodSyncedAt())
    }

    private fun section(section: String, lastSuccessAt: Long) = CatalogSyncStateEntity(
        section = section,
        accountKey = "key",
        lastSuccessAt = lastSuccessAt
    )
}
