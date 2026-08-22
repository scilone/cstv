package com.cstv.app.data.worker

import com.cstv.app.data.local.dao.BackfillCandidate
import com.cstv.app.data.local.dao.ExternalMetadataDao
import com.cstv.app.domain.model.HydrationReason
import com.cstv.app.domain.util.TimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ExternalMetadataBackfillSeederTest {
    private val dao: ExternalMetadataDao = mock()
    private val scheduler: ExternalMetadataHydrationScheduler = mock()
    private val timeProvider = object : TimeProvider { override fun nowMillis() = 1_000L }
    private val seeder = ExternalMetadataBackfillSeeder(dao, scheduler, timeProvider)

    /** Page keyset : un candidat par `linkKey` distinct, donc jamais absorbé par la déduplication. */
    private fun distinctPage(firstId: Int, size: Int = ExternalMetadataBackfillSeeder.PAGE_SIZE) =
        (firstId until firstId + size).map { BackfillCandidate(it, "link-$it") }

    @Test
    fun `an empty catalog enqueues nothing and reports convergence`() = runTest {
        whenever(dao.findMoviesMissingExternalMetadata(any(), any(), any())).thenReturn(emptyList())
        whenever(dao.findSeriesMissingExternalMetadata(any(), any(), any())).thenReturn(emptyList())

        val outcome = seeder.seed()

        verify(scheduler, never()).requestBatch(any(), any(), any())
        assertEquals(0, outcome.seeded)
        assertTrue(outcome.catalogExhausted)
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
        whenever(dao.findMoviesMissingExternalMetadata(eq(3), any(), any())).thenReturn(emptyList())
        whenever(dao.findSeriesMissingExternalMetadata(any(), any(), any())).thenReturn(emptyList())

        seeder.seed()

        verify(scheduler).requestBatch("movie", listOf(1, 3), HydrationReason.MISSING_METADATA)
    }

    @Test
    fun `series are seeded independently from movies`() = runTest {
        whenever(dao.findMoviesMissingExternalMetadata(any(), any(), any())).thenReturn(emptyList())
        whenever(dao.findSeriesMissingExternalMetadata(eq(0), any(), any())).thenReturn(listOf(BackfillCandidate(9, "show-2020")))
        whenever(dao.findSeriesMissingExternalMetadata(eq(9), any(), any())).thenReturn(emptyList())

        seeder.seed()

        verify(scheduler).requestBatch("series", listOf(9), HydrationReason.MISSING_METADATA)
    }

    // -------------------------------------------------------------------------------------------
    // T29 cycle backfill P0-3 : vraie pagination keyset jusqu'à la profondeur cible de travail prêt.
    // -------------------------------------------------------------------------------------------

    /**
     * Le cas qui vidait la file en production : une page pleine dont toutes les variantes partagent
     * la même œuvre ne produit qu'une seule demande. Le seeder doit continuer, pas s'arrêter là.
     */
    @Test
    fun `a page fully collapsed by deduplication does not stop the pass`() = runTest {
        val collapsed = (1..ExternalMetadataBackfillSeeder.PAGE_SIZE).map { BackfillCandidate(it, "same-work") }
        whenever(dao.findMoviesMissingExternalMetadata(eq(0), any(), any())).thenReturn(collapsed)
        whenever(dao.findMoviesMissingExternalMetadata(eq(ExternalMetadataBackfillSeeder.PAGE_SIZE), any(), any()))
            .thenReturn(listOf(BackfillCandidate(500, "another-work")))
        whenever(dao.findMoviesMissingExternalMetadata(eq(500), any(), any())).thenReturn(emptyList())
        whenever(dao.findSeriesMissingExternalMetadata(any(), any(), any())).thenReturn(emptyList())

        seeder.seed()

        verify(scheduler).requestBatch("movie", listOf(1), HydrationReason.MISSING_METADATA)
        verify(scheduler).requestBatch("movie", listOf(500), HydrationReason.MISSING_METADATA)
    }

    /** Les candidats utiles n'arrivent qu'en page 3 : ils doivent quand même être seedés. */
    @Test
    fun `candidates found only on the third page are still seeded`() = runTest {
        whenever(dao.findMoviesMissingExternalMetadata(eq(0), any(), any()))
            .thenReturn((1..ExternalMetadataBackfillSeeder.PAGE_SIZE).map { BackfillCandidate(it, "page-1") })
        whenever(dao.findMoviesMissingExternalMetadata(eq(100), any(), any()))
            .thenReturn((101..200).map { BackfillCandidate(it, "page-2") })
        whenever(dao.findMoviesMissingExternalMetadata(eq(200), any(), any()))
            .thenReturn(listOf(BackfillCandidate(201, "wanted-a"), BackfillCandidate(202, "wanted-b")))
        whenever(dao.findMoviesMissingExternalMetadata(eq(202), any(), any())).thenReturn(emptyList())
        whenever(dao.findSeriesMissingExternalMetadata(any(), any(), any())).thenReturn(emptyList())

        seeder.seed()

        verify(scheduler).requestBatch("movie", listOf(201, 202), HydrationReason.MISSING_METADATA)
    }

    /** `afterId` progresse strictement : aucune page n'est relue, aucun providerId n'est seedé deux fois. */
    @Test
    fun `the keyset cursor advances and never seeds the same providerId twice`() = runTest {
        whenever(dao.findMoviesMissingExternalMetadata(eq(0), any(), any())).thenReturn(distinctPage(1))
        whenever(dao.findMoviesMissingExternalMetadata(eq(100), any(), any())).thenReturn(distinctPage(101))
        whenever(dao.findMoviesMissingExternalMetadata(eq(200), any(), any())).thenReturn(emptyList())
        whenever(dao.findSeriesMissingExternalMetadata(any(), any(), any())).thenReturn(emptyList())

        seeder.seed()

        val kinds = argumentCaptor<String>()
        val ids = argumentCaptor<Collection<Int>>()
        verify(scheduler, atLeastOnce()).requestBatch(kinds.capture(), ids.capture(), any())
        val seeded = ids.allValues.flatten()
        assertEquals(200, seeded.size)
        assertEquals(seeded.size, seeded.toSet().size)
        assertEquals(listOf(1, 101), ids.allValues.map { it.first() }) // le curseur avance de page en page
        assertTrue(kinds.allValues.all { it == "movie" })
    }

    /** La profondeur cible borne la passe : pas de scan complet du catalogue à chaque réveil. */
    @Test
    fun `the pass stops as soon as the target ready depth is reached`() = runTest {
        whenever(dao.countReadyRequests(any())).thenReturn(0)
        whenever(dao.findMoviesMissingExternalMetadata(any(), any(), any())).thenAnswer { invocation ->
            distinctPage(invocation.getArgument<Int>(0) + 1)
        }
        whenever(dao.findSeriesMissingExternalMetadata(any(), any(), any())).thenAnswer { invocation ->
            distinctPage(invocation.getArgument<Int>(0) + 1)
        }

        val outcome = seeder.seed()

        assertEquals(ExternalMetadataBackfillSeeder.TARGET_READY_DEPTH, outcome.seeded)
        assertFalse(outcome.catalogExhausted) // arrêt sur la cible, pas sur la fin du catalogue
    }

    /** Une file déjà bien fournie ne déclenche aucune lecture du catalogue. */
    @Test
    fun `a queue already at the target depth reads no page at all`() = runTest {
        whenever(dao.countReadyRequests(any())).thenReturn(ExternalMetadataBackfillSeeder.TARGET_READY_DEPTH)

        val outcome = seeder.seed()

        verify(dao, never()).findMoviesMissingExternalMetadata(any(), any(), any())
        verify(dao, never()).findSeriesMissingExternalMetadata(any(), any(), any())
        assertEquals(0, outcome.seeded)
        assertFalse(outcome.catalogExhausted)
    }

    /** Aucun kind ne peut monopoliser le budget de pages : l'alternance est stricte. */
    @Test
    fun `movies and series both keep being seeded on a large catalog`() = runTest {
        whenever(dao.countReadyRequests(any())).thenReturn(0)
        whenever(dao.findMoviesMissingExternalMetadata(any(), any(), any())).thenAnswer { invocation ->
            distinctPage(invocation.getArgument<Int>(0) + 1)
        }
        whenever(dao.findSeriesMissingExternalMetadata(any(), any(), any())).thenAnswer { invocation ->
            distinctPage(invocation.getArgument<Int>(0) + 1)
        }

        seeder.seed()

        val kinds = argumentCaptor<String>()
        verify(scheduler, atLeastOnce()).requestBatch(kinds.capture(), any(), any())
        assertTrue(kinds.allValues.contains("movie"))
        assertTrue(kinds.allValues.contains("series"))
    }

    /** Une lecture en erreur ne doit jamais être lue comme une convergence : le réveil court doit rester. */
    @Test
    fun `a failing kind never reports the catalog as converged`() = runTest {
        whenever(dao.findMoviesMissingExternalMetadata(any(), any(), any())).thenAnswer { throw IllegalStateException("Room down") }
        whenever(dao.findSeriesMissingExternalMetadata(any(), any(), any())).thenReturn(emptyList())

        val outcome = seeder.seed()

        assertFalse(outcome.catalogExhausted)
    }

    /** Garde-fou dur contre le scan complet du catalogue quand chaque page se réduit à un représentant. */
    @Test
    fun `a catalog collapsing to one representative per page stops at the page budget`() = runTest {
        whenever(dao.countReadyRequests(any())).thenReturn(0)
        whenever(dao.findMoviesMissingExternalMetadata(any(), any(), any())).thenAnswer { invocation ->
            val afterId = invocation.getArgument<Int>(0)
            (afterId + 1..afterId + ExternalMetadataBackfillSeeder.PAGE_SIZE).map { BackfillCandidate(it, "work-$afterId") }
        }
        whenever(dao.findSeriesMissingExternalMetadata(any(), any(), any())).thenAnswer { invocation ->
            val afterId = invocation.getArgument<Int>(0)
            (afterId + 1..afterId + ExternalMetadataBackfillSeeder.PAGE_SIZE).map { BackfillCandidate(it, "show-$afterId") }
        }

        val outcome = seeder.seed()

        assertEquals(ExternalMetadataBackfillSeeder.MAX_PAGES_PER_SEED, outcome.seeded) // un représentant par page
        assertFalse(outcome.catalogExhausted)
    }
}
