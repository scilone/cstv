package com.cstv.app.data.worker

import com.cstv.app.data.local.dao.BackfillCandidate
import com.cstv.app.data.local.dao.ExternalMetadataDao
import com.cstv.app.domain.model.HydrationReason
import com.cstv.app.domain.util.TimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ExternalMetadataBackfillSeederTest {
    private val dao: ExternalMetadataDao = mock()
    private val scheduler: ExternalMetadataHydrationScheduler = mock()
    private val timeProvider = object : TimeProvider { override fun nowMillis() = 1_000L }
    private val seeder = ExternalMetadataBackfillSeeder(dao, scheduler, timeProvider)

    @Test
    fun `an empty catalog enqueues nothing`() = runTest {
        whenever(dao.findMoviesMissingExternalMetadata(any(), any(), any())).thenReturn(emptyList())
        whenever(dao.findSeriesMissingExternalMetadata(any(), any(), any())).thenReturn(emptyList())

        seeder.seed()

        verify(scheduler, org.mockito.kotlin.never()).requestBatch(any(), any(), any())
    }

    @Test
    fun `movies missing metadata are enqueued deduplicated by linkKey`() = runTest {
        whenever(dao.findMoviesMissingExternalMetadata(eq(0), any(), any())).thenReturn(
            listOf(
                BackfillCandidate(1, "dune-2021"),
                BackfillCandidate(2, "dune-2021"), // même œuvre, variante — un seul enqueue
                BackfillCandidate(3, ""), // pas encore normalisé (T21) — jamais groupé avec un autre
            ),
        )
        whenever(dao.findSeriesMissingExternalMetadata(any(), any(), any())).thenReturn(emptyList())

        seeder.seed()

        verify(scheduler).requestBatch("movie", listOf(1, 3), HydrationReason.MISSING_METADATA)
    }

    @Test
    fun `a pass is bounded to one page and enqueues it in one batch`() = runTest {
        val fullPage = (1..ExternalMetadataBackfillSeeder.PAGE_SIZE).map { BackfillCandidate(it, "link-$it") }
        whenever(dao.findMoviesMissingExternalMetadata(eq(0), any(), any())).thenReturn(fullPage)
        whenever(dao.findSeriesMissingExternalMetadata(any(), any(), any())).thenReturn(emptyList())

        seeder.seed()

        verify(scheduler).requestBatch(
            eq("movie"),
            eq((1..ExternalMetadataBackfillSeeder.PAGE_SIZE).toList()),
            eq(HydrationReason.MISSING_METADATA),
        )
    }

    @Test
    fun `series are seeded independently from movies`() = runTest {
        whenever(dao.findMoviesMissingExternalMetadata(any(), any(), any())).thenReturn(emptyList())
        whenever(dao.findSeriesMissingExternalMetadata(eq(0), any(), any())).thenReturn(listOf(BackfillCandidate(9, "show-2020")))

        seeder.seed()

        verify(scheduler).requestBatch("series", listOf(9), HydrationReason.MISSING_METADATA)
    }
}
