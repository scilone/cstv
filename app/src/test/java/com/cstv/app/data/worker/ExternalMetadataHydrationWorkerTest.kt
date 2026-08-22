package com.cstv.app.data.worker

import com.cstv.app.data.local.dao.ExternalMetadataDao
import com.cstv.app.data.local.dao.SeriesDao
import com.cstv.app.data.local.dao.VodDao
import com.cstv.app.data.local.entity.ExternalHydrationRequestEntity
import com.cstv.app.data.local.entity.ExternalMediaLinkEntity
import com.cstv.app.data.local.entity.VodStreamEntity
import com.cstv.app.domain.model.ExternalMatchHints
import com.cstv.app.domain.model.ExternalMetadataMatch
import com.cstv.app.domain.model.ExternalMetadataMatchOutcome
import com.cstv.app.domain.model.ExternalMetadataMatchRequest
import com.cstv.app.domain.repository.ExternalMetadataRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ExternalMetadataHydrationWorkerTest {

    private fun vodRow(streamId: Int, linkKey: String = "dune-2021") = VodStreamEntity(
        streamId = streamId, name = "Dune", streamIcon = null, rating = null, added = null, categoryId = "1",
        cachedAt = 0L, actors = "Timothée Chalamet, Zendaya", director = "Denis Villeneuve", genre = "Sci-Fi, Aventure",
        releaseYear = 2021, duration = "02:35:00", cleanTitle = "Dune", linkKey = linkKey,
    )

    @Test
    fun `an empty queue never calls the repository`() = runTest {
        val dao: ExternalMetadataDao = mock()
        val vodDao: VodDao = mock()
        val seriesDao: SeriesDao = mock()
        val repository: ExternalMetadataRepository = mock()
        whenever(dao.nextRequest(any())).thenReturn(null)

        val batchWasFull = ExternalMetadataHydrationWorker.drainQueue(dao, vodDao, seriesDao, repository) { 1_000L }

        assertFalse(batchWasFull)
        verify(repository, never()).match(any(), any(), any(), anyOrNull(), anyOrNull(), any(), any())
    }

    @Test
    fun `a matched movie is deleted from the queue and its hints are built from the Xtream row`() = runTest {
        val dao: ExternalMetadataDao = mock()
        val vodDao: VodDao = mock()
        val seriesDao: SeriesDao = mock()
        val repository: ExternalMetadataRepository = mock()
        val request = ExternalHydrationRequestEntity("movie", 42, "DETAIL_OPEN", 3, 1L, 1L, 0)
        whenever(dao.nextRequest(any())).thenReturn(request, null)
        whenever(vodDao.getStreamById(42)).thenReturn(vodRow(42))
        whenever(vodDao.getStreamsByLinkKey(eq("dune-2021"), eq(2021), any())).thenReturn(emptyList())
        whenever(repository.match(any(), any(), any(), anyOrNull(), anyOrNull(), any(), any())).thenReturn(
            ExternalMetadataMatchOutcome.Matched(ExternalMetadataMatch("5e37ba2a-1cda-4faf-9f10-335b2f6556a7", "movie", 92, "title+year+director", 1, 13)),
        )

        val batchWasFull = ExternalMetadataHydrationWorker.drainQueue(dao, vodDao, seriesDao, repository) { 1_000L }

        assertFalse(batchWasFull)
        verify(dao).deleteRequest("movie", 42)
        val hints = argumentCaptor<ExternalMatchHints>()
        // F45-R5 : priorité DETAIL_OPEN (3) -> allowRefresh=true, seul chemin autorisé à rafraîchir un hit local stale.
        verify(repository).match(eq("movie"), eq(42), eq("Dune"), eq(2021), eq("dune-2021"), hints.capture(), eq(true))
        assertEquals("Denis Villeneuve", hints.firstValue.director)
        assertEquals(listOf("Timothée Chalamet", "Zendaya"), hints.firstValue.actors)
        assertEquals(155, hints.firstValue.runtimeMinutes)
    }

    @Test
    fun `a background priority never allows a stale local hit to be refreshed`() = runTest {
        // F45-R5 : NEW_IPTV_MEDIA/MISSING_METADATA ne doivent jamais rafraîchir une donnée stale
        // (§7.1/§7.5) — seul DETAIL_OPEN le peut. Le worker doit donc appeler `match()` avec
        // `allowRefresh = false` pour cette priorité, quel que soit l'état local.
        val dao: ExternalMetadataDao = mock()
        val vodDao: VodDao = mock()
        val seriesDao: SeriesDao = mock()
        val repository: ExternalMetadataRepository = mock()
        val request = ExternalHydrationRequestEntity("movie", 55, "MISSING_METADATA", 1, 1L, 1L, 0)
        whenever(dao.nextRequest(any())).thenReturn(request, null)
        whenever(vodDao.getStreamById(55)).thenReturn(vodRow(55, linkKey = "bg-55"))
        whenever(repository.match(any(), any(), any(), anyOrNull(), anyOrNull(), any(), any())).thenReturn(ExternalMetadataMatchOutcome.Unresolved)

        ExternalMetadataHydrationWorker.drainQueue(dao, vodDao, seriesDao, repository) { 1_000L }

        verify(repository).match(eq("movie"), eq(55), eq("Dune"), eq(2021), eq("bg-55"), any(), eq(false))
    }

    @Test
    fun `a source row deleted from the catalog is dropped without calling the repository`() = runTest {
        val dao: ExternalMetadataDao = mock()
        val vodDao: VodDao = mock()
        val seriesDao: SeriesDao = mock()
        val repository: ExternalMetadataRepository = mock()
        val request = ExternalHydrationRequestEntity("movie", 99, "MISSING_METADATA", 1, 1L, 1L, 0)
        whenever(dao.nextRequest(any())).thenReturn(request, null)
        whenever(vodDao.getStreamById(99)).thenReturn(null)

        ExternalMetadataHydrationWorker.drainQueue(dao, vodDao, seriesDao, repository) { 1_000L }

        verify(dao).deleteRequest("movie", 99)
        verify(repository, never()).match(any(), any(), any(), anyOrNull(), anyOrNull(), any(), any())
    }

    @Test
    fun `a failing item is requeued with backoff and never blocks the next one`() = runTest {
        val dao: ExternalMetadataDao = mock()
        val vodDao: VodDao = mock()
        val seriesDao: SeriesDao = mock()
        val repository: ExternalMetadataRepository = mock()
        val failing = ExternalHydrationRequestEntity("movie", 1, "MISSING_METADATA", 1, 1L, 1L, 0)
        val next = ExternalHydrationRequestEntity("movie", 2, "MISSING_METADATA", 1, 1L, 1L, 0)
        whenever(dao.nextRequest(any())).thenReturn(failing, next, null)
        whenever(vodDao.getStreamById(1)).thenReturn(vodRow(1, linkKey = "solo-1"))
        whenever(vodDao.getStreamById(2)).thenReturn(vodRow(2, linkKey = "solo-2"))
        whenever(repository.match(any(), any(), any(), anyOrNull(), anyOrNull(), any(), any())).thenAnswer { invocation ->
            if (invocation.getArgument<Int>(1) == 1) {
                throw RuntimeException("network down")
            } else {
                ExternalMetadataMatchOutcome.Matched(ExternalMetadataMatch("6f48cb3b-2ddb-5fbf-a021-446c3f6667b8", "movie", 90, "title+year", 1, null))
            }
        }
        whenever(vodDao.getStreamsByLinkKey(any(), anyOrNull(), any())).thenReturn(emptyList())

        ExternalMetadataHydrationWorker.drainQueue(dao, vodDao, seriesDao, repository) { 1_000L }

        verify(dao, never()).deleteRequest("movie", 1)
        val requeued = argumentCaptor<ExternalHydrationRequestEntity>()
        verify(dao).upsertRequest(requeued.capture())
        assertEquals(1, requeued.firstValue.attemptCount)
        assertTrue(requeued.firstValue.nextAttemptAt > 1_000L)
        verify(dao).deleteRequest("movie", 2) // l'item suivant est bien traité malgré l'échec du premier
    }

    /**
     * T29 §7.6/§8.10 : un `retry` (impossibilité technique temporaire) doit rester en file — jamais
     * retiré comme un résultat métier — et son délai vient du backend (`Retry-After`) quand fourni.
     */
    @Test
    fun `T29 a single retry item is requeued using the backend retryAfter and never deleted`() = runTest {
        val dao: ExternalMetadataDao = mock()
        val vodDao: VodDao = mock()
        val seriesDao: SeriesDao = mock()
        val repository: ExternalMetadataRepository = mock()
        val request = ExternalHydrationRequestEntity("movie", 7, "MISSING_METADATA", 1, 1L, 1L, 0)
        whenever(dao.nextRequest(any())).thenReturn(request, null)
        whenever(vodDao.getStreamById(7)).thenReturn(vodRow(7, linkKey = "retry-7"))
        whenever(repository.match(any(), any(), any(), anyOrNull(), anyOrNull(), any(), any()))
            .thenReturn(ExternalMetadataMatchOutcome.Retry(4_000L))

        ExternalMetadataHydrationWorker.drainQueue(dao, vodDao, seriesDao, repository) { 1_000L }

        verify(dao, never()).deleteRequest("movie", 7)
        val requeued = argumentCaptor<ExternalHydrationRequestEntity>()
        verify(dao).upsertRequest(requeued.capture())
        assertEquals(1, requeued.firstValue.attemptCount)
        assertEquals(5_000L, requeued.firstValue.nextAttemptAt) // now(1_000) + retryAfterMillis(4_000)
    }

    @Test
    fun `T29 a batch requeues only the retry item and processes matched-unresolved-retry independently`() = runTest {
        val dao: ExternalMetadataDao = mock()
        val vodDao: VodDao = mock()
        val seriesDao: SeriesDao = mock()
        val repository: ExternalMetadataRepository = mock()
        val matched = ExternalHydrationRequestEntity("movie", 1, "MISSING_METADATA", 1, 1L, 1L, 0)
        val unresolved = ExternalHydrationRequestEntity("movie", 2, "MISSING_METADATA", 1, 1L, 1L, 0)
        val retry = ExternalHydrationRequestEntity("movie", 3, "MISSING_METADATA", 1, 1L, 1L, 0)
        whenever(dao.nextRequests(any(), any())).thenReturn(listOf(matched, unresolved, retry), emptyList())
        whenever(vodDao.getStreamById(1)).thenReturn(vodRow(1, "batch-1"))
        whenever(vodDao.getStreamById(2)).thenReturn(vodRow(2, "batch-2"))
        whenever(vodDao.getStreamById(3)).thenReturn(vodRow(3, "batch-3"))
        whenever(vodDao.getStreamsByLinkKey(any(), anyOrNull(), any())).thenReturn(emptyList())
        whenever(repository.matchBatch(any())).thenReturn(
            listOf(
                ExternalMetadataMatchOutcome.Matched(ExternalMetadataMatch("5e37ba2a-1cda-4faf-9f10-335b2f6556a7", "movie", 90, "title+year", 1, null)),
                ExternalMetadataMatchOutcome.Unresolved,
                ExternalMetadataMatchOutcome.Retry(null), // pas de Retry-After connu -> backoff F45
            ),
        )

        ExternalMetadataHydrationWorker.drainQueue(dao, vodDao, seriesDao, repository) { 1_000L }

        verify(dao).deleteRequest("movie", 1)
        verify(dao).deleteRequest("movie", 2)
        verify(dao, never()).deleteRequest("movie", 3)
        val requeued = argumentCaptor<ExternalHydrationRequestEntity>()
        verify(dao).upsertRequest(requeued.capture())
        assertEquals(3, requeued.firstValue.providerId)
        assertEquals(1, requeued.firstValue.attemptCount)
        assertEquals(1_000L + ExternalMetadataHydrationWorker.backoffDelayMillis(1), requeued.firstValue.nextAttemptAt)
    }

    @Test
    fun `a fresh match propagates the same externalId to same-linkKey siblings but not to itself`() = runTest {
        val dao: ExternalMetadataDao = mock()
        val vodDao: VodDao = mock()
        val seriesDao: SeriesDao = mock()
        val repository: ExternalMetadataRepository = mock()
        val request = ExternalHydrationRequestEntity("movie", 42, "DETAIL_OPEN", 3, 1L, 1L, 0)
        whenever(dao.nextRequest(any())).thenReturn(request, null)
        whenever(vodDao.getStreamById(42)).thenReturn(vodRow(42))
        whenever(vodDao.getStreamsByLinkKey(eq("dune-2021"), eq(2021), any()))
            .thenReturn(listOf(vodRow(42), vodRow(43), vodRow(44))) // le groupe inclut l'item lui-même
        whenever(repository.match(any(), any(), any(), anyOrNull(), anyOrNull(), any(), any())).thenReturn(
            ExternalMetadataMatchOutcome.Matched(ExternalMetadataMatch("5e37ba2a-1cda-4faf-9f10-335b2f6556a7", "movie", 92, "title+year", 1, 13)),
        )

        ExternalMetadataHydrationWorker.drainQueue(dao, vodDao, seriesDao, repository) { 1_000L }

        val links = argumentCaptor<ExternalMediaLinkEntity>()
        verify(dao, times(2)).upsertLink(links.capture())
        val propagatedIds = links.allValues.map { it.providerId }.toSet()
        assertEquals(setOf(43, 44), propagatedIds)
    }

    @Test
    fun `propagation is skipped when the match was resolved from the local cache (fromNetwork false)`() = runTest {
        // F45-R4/R5 : un hit local renvoie désormais une vraie confidence (plus jamais null par
        // construction, voir ExternalMetadataRepositoryImplTest) — seul `fromNetwork = false` signale
        // encore qu'aucun travail réseau n'a eu lieu, donc rien de neuf à propager aux variantes.
        val dao: ExternalMetadataDao = mock()
        val vodDao: VodDao = mock()
        val seriesDao: SeriesDao = mock()
        val repository: ExternalMetadataRepository = mock()
        val request = ExternalHydrationRequestEntity("movie", 42, "DETAIL_OPEN", 3, 1L, 1L, 0)
        whenever(dao.nextRequest(any())).thenReturn(request, null)
        whenever(vodDao.getStreamById(42)).thenReturn(vodRow(42))
        whenever(repository.match(any(), any(), any(), anyOrNull(), anyOrNull(), any(), any())).thenReturn(
            ExternalMetadataMatchOutcome.Matched(ExternalMetadataMatch("5e37ba2a-1cda-4faf-9f10-335b2f6556a7", "movie", 90, "title+year", 1, 13, fromNetwork = false)),
        )

        ExternalMetadataHydrationWorker.drainQueue(dao, vodDao, seriesDao, repository) { 1_000L }

        verify(dao, never()).upsertLink(any())
        verify(vodDao, never()).getStreamsByLinkKey(any(), anyOrNull(), any())
    }

    @Test
    fun `due requests are sent in one compact backend batch`() = runTest {
        val dao: ExternalMetadataDao = mock()
        val vodDao: VodDao = mock()
        val seriesDao: SeriesDao = mock()
        val repository: ExternalMetadataRepository = mock()
        val first = ExternalHydrationRequestEntity("movie", 1, "MISSING_METADATA", 1, 1L, 1L, 0)
        val second = ExternalHydrationRequestEntity("movie", 2, "MISSING_METADATA", 1, 1L, 1L, 0)
        whenever(dao.nextRequests(any(), any())).thenReturn(listOf(first, second), emptyList())
        whenever(vodDao.getStreamById(1)).thenReturn(vodRow(1, "batch-1"))
        whenever(vodDao.getStreamById(2)).thenReturn(vodRow(2, "batch-2"))
        whenever(repository.matchBatch(any())).thenReturn(listOf(ExternalMetadataMatchOutcome.Unresolved, ExternalMetadataMatchOutcome.Unresolved))

        ExternalMetadataHydrationWorker.drainQueue(dao, vodDao, seriesDao, repository) { 1_000L }

        val requests = argumentCaptor<List<ExternalMetadataMatchRequest>>()
        verify(repository).matchBatch(requests.capture())
        assertEquals(listOf(1, 2), requests.firstValue.map { it.providerId })
        assertEquals(listOf("Dune", "Dune"), requests.firstValue.map { it.title })
        assertTrue(requests.firstValue.all { it.allowRefresh == false })
        verify(dao).deleteRequest("movie", 1)
        verify(dao).deleteRequest("movie", 2)
    }

    @Test
    fun `nextWakeupDelayMillis is null when the queue is empty`() = runTest {
        // F45-R2 : rien à réveiller, le worker ne doit se reprogrammer sous aucune forme.
        val dao: ExternalMetadataDao = mock()
        whenever(dao.earliestNextAttemptAt()).thenReturn(null)

        val delay = ExternalMetadataHydrationWorker.nextWakeupDelayMillis(dao, 1_000L)

        assertNull(delay)
    }

    @Test
    fun `nextWakeupDelayMillis is zero when an item is already due`() = runTest {
        val dao: ExternalMetadataDao = mock()
        whenever(dao.earliestNextAttemptAt()).thenReturn(500L) // échéance déjà passée

        val delay = ExternalMetadataHydrationWorker.nextWakeupDelayMillis(dao, 1_000L)

        assertEquals(0L, delay)
    }

    @Test
    fun `nextWakeupDelayMillis matches the earliest backoff still pending`() = runTest {
        // F45-R2 : c'est ce délai qui doit devenir le setInitialDelay du prochain WorkRequest —
        // avant le fix, aucun réveil n'était programmé et ces items restaient orphelins en Room.
        val dao: ExternalMetadataDao = mock()
        whenever(dao.earliestNextAttemptAt()).thenReturn(1_600_000L)

        val delay = ExternalMetadataHydrationWorker.nextWakeupDelayMillis(dao, 1_000L)

        assertEquals(1_599_000L, delay)
    }

    @Test
    fun `backoff grows exponentially and is capped at 6 hours`() {
        assertEquals(10 * 60_000L, ExternalMetadataHydrationWorker.backoffDelayMillis(1))
        assertEquals(20 * 60_000L, ExternalMetadataHydrationWorker.backoffDelayMillis(2))
        assertEquals(40 * 60_000L, ExternalMetadataHydrationWorker.backoffDelayMillis(3))
        assertEquals(360 * 60_000L, ExternalMetadataHydrationWorker.backoffDelayMillis(10))
    }

    @Test
    fun `duration parsing tolerates HH-MM-SS and MM-SS, rejects garbage`() {
        assertEquals(155, ExternalMetadataHydrationWorker.parseDurationMinutes("02:35:00"))
        assertEquals(155, ExternalMetadataHydrationWorker.parseDurationMinutes("02:34:30")) // arrondi supérieur
        assertEquals(45, ExternalMetadataHydrationWorker.parseDurationMinutes("45:00"))
        assertEquals(null, ExternalMetadataHydrationWorker.parseDurationMinutes(null))
        assertEquals(null, ExternalMetadataHydrationWorker.parseDurationMinutes(""))
        assertEquals(null, ExternalMetadataHydrationWorker.parseDurationMinutes("not a duration"))
        assertEquals(null, ExternalMetadataHydrationWorker.parseDurationMinutes("00:00:00"))
    }

    @Test
    fun `names and director parsing split on comma or slash and trim blanks`() {
        assertEquals(listOf("Timothée Chalamet", "Zendaya"), ExternalMetadataHydrationWorker.parseNames("Timothée Chalamet, Zendaya"))
        assertEquals(emptyList<String>(), ExternalMetadataHydrationWorker.parseNames(null))
        assertEquals(emptyList<String>(), ExternalMetadataHydrationWorker.parseNames("  "))
        assertEquals("Denis Villeneuve", ExternalMetadataHydrationWorker.firstName("Denis Villeneuve/Someone Else"))
        assertEquals(null, ExternalMetadataHydrationWorker.firstName(null))
    }
}
