package com.cstv.app.data.worker

import com.cstv.app.data.local.dao.ExternalMetadataDao
import com.cstv.app.data.local.dao.SeriesDao
import com.cstv.app.data.local.dao.VodDao
import com.cstv.app.data.local.entity.ExternalHydrationRequestEntity
import com.cstv.app.data.local.entity.ExternalMediaLinkEntity
import com.cstv.app.data.local.entity.VodStreamEntity
import com.cstv.app.domain.model.CatalogThrottledException
import com.cstv.app.domain.model.ExternalMatchHints
import com.cstv.app.domain.model.ExternalMetadataCoverage
import com.cstv.app.domain.model.ExternalMetadataMatch
import com.cstv.app.domain.model.ExternalMetadataMatchOutcome
import com.cstv.app.domain.model.ExternalMetadataMatchRequest
import com.cstv.app.domain.model.HydrationRetryReason
import com.cstv.app.domain.repository.ExternalMetadataRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
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

// `TestCoroutineScheduler.currentTime` : horloge virtuelle des tests de cadence T29 (§3).
@kotlinx.coroutines.ExperimentalCoroutinesApi
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

        val batchWasFull = ExternalMetadataHydrationWorker.drainQueue(dao, vodDao, seriesDao, repository) { 1_000L }.runFull

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

        val batchWasFull = ExternalMetadataHydrationWorker.drainQueue(dao, vodDao, seriesDao, repository) { 1_000L }.runFull

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

    // ---------------------------------------------------------------------------------------
    // T29 débit §2 : un HTTP 429 CSTV est un problème de cadence, jamais un échec du média.
    // ---------------------------------------------------------------------------------------

    @Test
    fun `T29 debit a backend throttle keeps the batch queued on the server Retry-After without any backoff`() = runTest {
        val dao: ExternalMetadataDao = mock()
        val vodDao: VodDao = mock()
        val seriesDao: SeriesDao = mock()
        val repository: ExternalMetadataRepository = mock()
        // attemptCount déjà à 2 : il doit rester intact, un throttle n'est pas une tentative ratée.
        val first = ExternalHydrationRequestEntity("movie", 1, "MISSING_METADATA", 1, 1L, 1L, 2)
        val second = ExternalHydrationRequestEntity("movie", 2, "MISSING_METADATA", 1, 1L, 1L, 2)
        whenever(dao.nextRequests(any(), any())).thenReturn(listOf(first, second), emptyList())
        whenever(vodDao.getStreamById(1)).thenReturn(vodRow(1, "throttle-1"))
        whenever(vodDao.getStreamById(2)).thenReturn(vodRow(2, "throttle-2"))
        whenever(repository.matchBatch(any())).thenAnswer { throw CatalogThrottledException(45_000L) }

        val result = ExternalMetadataHydrationWorker.drainQueue(dao, vodDao, seriesDao, repository) { 1_000L }

        assertEquals(45_000L, result.throttleDelayMillis)
        verify(dao, never()).deleteRequest(any(), any())
        val requeued = argumentCaptor<ExternalHydrationRequestEntity>()
        verify(dao, times(2)).upsertRequest(requeued.capture())
        assertEquals(setOf(1, 2), requeued.allValues.map { it.providerId }.toSet())
        requeued.allValues.forEach { entity ->
            assertEquals(2, entity.attemptCount) // jamais incrémenté : pas d'exponentiation via attemptCount
            assertEquals(46_000L, entity.nextAttemptAt) // now(1 000) + Retry-After(45 000)
            assertTrue(entity.nextAttemptAt < 1_000L + ExternalMetadataHydrationWorker.backoffDelayMillis(1))
        }
    }

    @Test
    fun `T29 debit a throttle without a usable Retry-After falls back on a short cadence delay, never the backoff`() = runTest {
        val dao: ExternalMetadataDao = mock()
        val vodDao: VodDao = mock()
        val seriesDao: SeriesDao = mock()
        val repository: ExternalMetadataRepository = mock()
        val request = ExternalHydrationRequestEntity("movie", 9, "MISSING_METADATA", 1, 1L, 1L, 0)
        whenever(dao.nextRequests(any(), any())).thenReturn(listOf(request), emptyList())
        whenever(vodDao.getStreamById(9)).thenReturn(vodRow(9, "throttle-9"))
        whenever(repository.matchBatch(any())).thenAnswer { throw CatalogThrottledException(null) }

        val result = ExternalMetadataHydrationWorker.drainQueue(dao, vodDao, seriesDao, repository) { 1_000L }

        assertEquals(ExternalMetadataHydrationWorker.THROTTLE_FALLBACK_DELAY_MILLIS, result.throttleDelayMillis)
        val requeued = argumentCaptor<ExternalHydrationRequestEntity>()
        verify(dao).upsertRequest(requeued.capture())
        assertEquals(0, requeued.firstValue.attemptCount)
        assertEquals(1_000L + ExternalMetadataHydrationWorker.THROTTLE_FALLBACK_DELAY_MILLIS, requeued.firstValue.nextAttemptAt)
    }

    @Test
    fun `T29 debit a plain network failure on a batch keeps the F45 exponential backoff`() = runTest {
        // Non-régression : seul le 429 change de traitement — une vraie panne garde le backoff.
        val dao: ExternalMetadataDao = mock()
        val vodDao: VodDao = mock()
        val seriesDao: SeriesDao = mock()
        val repository: ExternalMetadataRepository = mock()
        val request = ExternalHydrationRequestEntity("movie", 4, "MISSING_METADATA", 1, 1L, 1L, 0)
        whenever(dao.nextRequests(any(), any())).thenReturn(listOf(request), emptyList())
        whenever(vodDao.getStreamById(4)).thenReturn(vodRow(4, "down-4"))
        whenever(repository.matchBatch(any())).thenAnswer { throw RuntimeException("network down") }

        val result = ExternalMetadataHydrationWorker.drainQueue(dao, vodDao, seriesDao, repository) { 1_000L }

        assertNull(result.throttleDelayMillis)
        val requeued = argumentCaptor<ExternalHydrationRequestEntity>()
        verify(dao).upsertRequest(requeued.capture())
        assertEquals(1, requeued.firstValue.attemptCount)
        assertEquals(1_000L + ExternalMetadataHydrationWorker.backoffDelayMillis(1), requeued.firstValue.nextAttemptAt)
    }

    // ---------------------------------------------------------------------------------------
    // T29 débit §3 : cadence préventive — on borne l'intervalle entre les START de requête.
    // Tous les tests avancent en temps virtuel (`runTest`) : aucune attente réelle.
    // ---------------------------------------------------------------------------------------

    /** Repository de test : enregistre l'instant virtuel de chaque appel et simule sa durée. */
    private class PacingRepository(
        private val scheduler: TestCoroutineScheduler,
        private val batchDurationMillis: Long,
        private val fromNetwork: Boolean = true,
    ) : ExternalMetadataRepository {
        val batchStartedAt = mutableListOf<Long>()

        override suspend fun matchBatch(requests: List<ExternalMetadataMatchRequest>): List<ExternalMetadataMatchOutcome> {
            batchStartedAt += scheduler.currentTime
            delay(batchDurationMillis)
            return requests.map {
                ExternalMetadataMatchOutcome.Matched(
                    ExternalMetadataMatch("5e37ba2a-1cda-4faf-9f10-335b2f6556a7", it.kind, 90, "title+year", 1, null, fromNetwork = fromNetwork),
                )
            }
        }

        override suspend fun match(
            kind: String, providerId: Int, title: String, year: Int?, linkKey: String?,
            hints: ExternalMatchHints, allowRefresh: Boolean,
        ): ExternalMetadataMatchOutcome = throw UnsupportedOperationException("unused")

        override suspend fun hydrateSeriesSeasons(externalId: String) = Unit
        override fun observeCoverage(): Flow<ExternalMetadataCoverage> = emptyFlow()
    }

    private suspend fun pacingDao(vararg batches: List<ExternalHydrationRequestEntity>): ExternalMetadataDao = mock<ExternalMetadataDao>().also { dao ->
        val stub = whenever(dao.nextRequests(any(), any()))
        batches.fold(stub) { chain, batch -> chain.thenReturn(batch) }.thenReturn(emptyList())
    }

    @Test
    fun `T29 debit a fast batch waits the remaining cadence before starting the next request`() = runTest {
        val dao = pacingDao(
            listOf(ExternalHydrationRequestEntity("movie", 1, "MISSING_METADATA", 1, 1L, 1L, 0)),
            listOf(ExternalHydrationRequestEntity("movie", 2, "MISSING_METADATA", 1, 1L, 1L, 0)),
        )
        val vodDao: VodDao = mock()
        val seriesDao: SeriesDao = mock()
        whenever(vodDao.getStreamById(any())).thenReturn(vodRow(1, "pace-fast"))
        whenever(vodDao.getStreamsByLinkKey(any(), anyOrNull(), any())).thenReturn(emptyList())
        val repository = PacingRepository(testScheduler, batchDurationMillis = 200L)

        ExternalMetadataHydrationWorker.drainQueue(dao, vodDao, seriesDao, repository) { testScheduler.currentTime }

        assertEquals(2, repository.batchStartedAt.size)
        assertEquals(0L, repository.batchStartedAt[0]) // la première requête ne doit jamais être retardée
        assertEquals(
            ExternalMetadataHydrationWorker.MIN_BATCH_INTERVAL_MILLIS,
            repository.batchStartedAt[1] - repository.batchStartedAt[0],
        )
    }

    @Test
    fun `T29 debit a batch longer than the cadence adds no extra wait at all`() = runTest {
        val dao = pacingDao(
            listOf(ExternalHydrationRequestEntity("movie", 1, "MISSING_METADATA", 1, 1L, 1L, 0)),
            listOf(ExternalHydrationRequestEntity("movie", 2, "MISSING_METADATA", 1, 1L, 1L, 0)),
        )
        val vodDao: VodDao = mock()
        val seriesDao: SeriesDao = mock()
        whenever(vodDao.getStreamById(any())).thenReturn(vodRow(1, "pace-slow"))
        whenever(vodDao.getStreamsByLinkKey(any(), anyOrNull(), any())).thenReturn(emptyList())
        val repository = PacingRepository(testScheduler, batchDurationMillis = 4_000L)

        ExternalMetadataHydrationWorker.drainQueue(dao, vodDao, seriesDao, repository) { testScheduler.currentTime }

        // 4 s de traitement > 2 s de cadence : le lot suivant part immédiatement après le précédent.
        assertEquals(4_000L, repository.batchStartedAt[1] - repository.batchStartedAt[0])
    }

    @Test
    fun `T29 debit a batch fully served by local links never imposes the network cadence`() = runTest {
        val dao = pacingDao(
            listOf(ExternalHydrationRequestEntity("movie", 1, "MISSING_METADATA", 1, 1L, 1L, 0)),
            listOf(ExternalHydrationRequestEntity("movie", 2, "MISSING_METADATA", 1, 1L, 1L, 0)),
        )
        val vodDao: VodDao = mock()
        val seriesDao: SeriesDao = mock()
        whenever(vodDao.getStreamById(any())).thenReturn(vodRow(1, "pace-local"))
        val repository = PacingRepository(testScheduler, batchDurationMillis = 0L, fromNetwork = false)

        ExternalMetadataHydrationWorker.drainQueue(dao, vodDao, seriesDao, repository) { testScheduler.currentTime }

        assertEquals(0L, repository.batchStartedAt[1] - repository.batchStartedAt[0])
    }

    /**
     * T29 cycle backfill P1 : la cadence doit survivre à la fin d'un run. Deux `drainQueue`
     * successifs partagent le même portillon, comme deux runs successifs du worker partagent le
     * même singleton Hilt et le même stockage.
     */
    @Test
    fun `T29 cycle a new run right after the previous one still honours the 2s cadence`() = runTest {
        val vodDao: VodDao = mock()
        val seriesDao: SeriesDao = mock()
        whenever(vodDao.getStreamById(any())).thenReturn(vodRow(1, "pace-across-runs"))
        whenever(vodDao.getStreamsByLinkKey(any(), anyOrNull(), any())).thenReturn(emptyList())
        val repository = PacingRepository(testScheduler, batchDurationMillis = 100L)
        val cadence = ExternalMetadataBatchCadence(InMemoryBatchCadenceStore())

        val firstRun = pacingDao(listOf(ExternalHydrationRequestEntity("movie", 1, "MISSING_METADATA", 1, 1L, 1L, 0)))
        ExternalMetadataHydrationWorker.drainQueue(dao = firstRun, vodDao = vodDao, seriesDao = seriesDao, repository = repository, cadence = cadence) { testScheduler.currentTime }
        val secondRun = pacingDao(listOf(ExternalHydrationRequestEntity("movie", 2, "MISSING_METADATA", 1, 1L, 1L, 0)))
        ExternalMetadataHydrationWorker.drainQueue(dao = secondRun, vodDao = vodDao, seriesDao = seriesDao, repository = repository, cadence = cadence) { testScheduler.currentTime }

        assertEquals(2, repository.batchStartedAt.size)
        assertEquals(
            ExternalMetadataHydrationWorker.MIN_BATCH_INTERVAL_MILLIS,
            repository.batchStartedAt[1] - repository.batchStartedAt[0],
        )
    }

    @Test
    fun `T29 cycle a run starting more than the cadence later waits for nothing`() = runTest {
        val vodDao: VodDao = mock()
        val seriesDao: SeriesDao = mock()
        whenever(vodDao.getStreamById(any())).thenReturn(vodRow(1, "pace-late-run"))
        whenever(vodDao.getStreamsByLinkKey(any(), anyOrNull(), any())).thenReturn(emptyList())
        val repository = PacingRepository(testScheduler, batchDurationMillis = 0L)
        // Départ réseau mémorisé 3 s avant le début du run : la cadence est déjà satisfaite.
        val cadence = ExternalMetadataBatchCadence(InMemoryBatchCadenceStore(initialStartedAt = -3_000L))

        val dao = pacingDao(listOf(ExternalHydrationRequestEntity("movie", 1, "MISSING_METADATA", 1, 1L, 1L, 0)))
        ExternalMetadataHydrationWorker.drainQueue(dao = dao, vodDao = vodDao, seriesDao = seriesDao, repository = repository, cadence = cadence) { testScheduler.currentTime }

        assertEquals(listOf(0L), repository.batchStartedAt)
    }

    /** P1 : mémoire restaurée depuis le stockage (process redémarré) — le premier lot attend quand même. */
    @Test
    fun `T29 cycle a cadence restored from storage still delays the very first batch of a fresh process`() = runTest {
        val vodDao: VodDao = mock()
        val seriesDao: SeriesDao = mock()
        whenever(vodDao.getStreamById(any())).thenReturn(vodRow(1, "pace-restored"))
        whenever(vodDao.getStreamsByLinkKey(any(), anyOrNull(), any())).thenReturn(emptyList())
        val repository = PacingRepository(testScheduler, batchDurationMillis = 0L)
        val cadence = ExternalMetadataBatchCadence(InMemoryBatchCadenceStore(initialStartedAt = -500L))

        val dao = pacingDao(listOf(ExternalHydrationRequestEntity("movie", 1, "MISSING_METADATA", 1, 1L, 1L, 0)))
        ExternalMetadataHydrationWorker.drainQueue(dao = dao, vodDao = vodDao, seriesDao = seriesDao, repository = repository, cadence = cadence) { testScheduler.currentTime }

        assertEquals(listOf(1_500L), repository.batchStartedAt) // 2 000 - 500 déjà écoulés
    }

    // ---------------------------------------------------------------------------------------
    // T29 cycle backfill P0-1 : un retry de deadline n'est pas une tentative ratée du média.
    // ---------------------------------------------------------------------------------------

    @Test
    fun `T29 cycle a batch deadline retry keeps attemptCount untouched and honours the backend delay`() = runTest {
        val dao: ExternalMetadataDao = mock()
        val vodDao: VodDao = mock()
        val seriesDao: SeriesDao = mock()
        val repository: ExternalMetadataRepository = mock()
        // attemptCount déjà à 3 : il doit rester intact, la deadline n'est pas un échec du média.
        val request = ExternalHydrationRequestEntity("movie", 11, "MISSING_METADATA", 1, 1L, 1L, 3)
        whenever(dao.nextRequests(any(), any())).thenReturn(listOf(request), emptyList())
        whenever(vodDao.getStreamById(11)).thenReturn(vodRow(11, "deadline-11"))
        whenever(repository.matchBatch(any())).thenReturn(
            listOf(ExternalMetadataMatchOutcome.Retry(30_000L, HydrationRetryReason.BATCH_DEADLINE)),
        )

        ExternalMetadataHydrationWorker.drainQueue(dao, vodDao, seriesDao, repository) { 1_000L }

        val requeued = argumentCaptor<ExternalHydrationRequestEntity>()
        verify(dao).upsertRequest(requeued.capture())
        assertEquals(3, requeued.firstValue.attemptCount)
        assertEquals(31_000L, requeued.firstValue.nextAttemptAt) // now(1 000) + retryAfter(30 000)
        verify(dao, never()).deleteRequest(any(), any()) // jamais persisté comme unresolved
    }

    @Test
    fun `T29 cycle a batch deadline retry without a delay falls back on a short one, never the backoff`() = runTest {
        val dao: ExternalMetadataDao = mock()
        val vodDao: VodDao = mock()
        val seriesDao: SeriesDao = mock()
        val repository: ExternalMetadataRepository = mock()
        val request = ExternalHydrationRequestEntity("movie", 12, "MISSING_METADATA", 1, 1L, 1L, 0)
        whenever(dao.nextRequests(any(), any())).thenReturn(listOf(request), emptyList())
        whenever(vodDao.getStreamById(12)).thenReturn(vodRow(12, "deadline-12"))
        whenever(repository.matchBatch(any())).thenReturn(
            listOf(ExternalMetadataMatchOutcome.Retry(null, HydrationRetryReason.BATCH_DEADLINE)),
        )

        ExternalMetadataHydrationWorker.drainQueue(dao, vodDao, seriesDao, repository) { 1_000L }

        val requeued = argumentCaptor<ExternalHydrationRequestEntity>()
        verify(dao).upsertRequest(requeued.capture())
        assertEquals(0, requeued.firstValue.attemptCount)
        assertEquals(1_000L + ExternalMetadataHydrationWorker.BATCH_DEADLINE_FALLBACK_DELAY_MILLIS, requeued.firstValue.nextAttemptAt)
        assertTrue(requeued.firstValue.nextAttemptAt < 1_000L + ExternalMetadataHydrationWorker.backoffDelayMillis(1))
    }

    /** Le scénario de production : un même média repoussé par la deadline batch après batch. */
    @Test
    fun `T29 cycle four successive deadline retries never inflate attemptCount`() = runTest {
        val dao: ExternalMetadataDao = mock()
        val vodDao: VodDao = mock()
        val seriesDao: SeriesDao = mock()
        val repository: ExternalMetadataRepository = mock()
        whenever(vodDao.getStreamById(5)).thenReturn(vodRow(5, "deadline-loop"))
        whenever(repository.matchBatch(any())).thenReturn(
            listOf(ExternalMetadataMatchOutcome.Retry(30_000L, HydrationRetryReason.BATCH_DEADLINE)),
        )
        var request = ExternalHydrationRequestEntity("movie", 5, "MISSING_METADATA", 1, 1L, 1L, 0)

        repeat(4) {
            val current = request
            whenever(dao.nextRequests(any(), any())).thenReturn(listOf(current), emptyList())
            ExternalMetadataHydrationWorker.drainQueue(dao, vodDao, seriesDao, repository) { 1_000L }
            val requeued = argumentCaptor<ExternalHydrationRequestEntity>()
            verify(dao, times(it + 1)).upsertRequest(requeued.capture())
            request = requeued.lastValue
        }

        assertEquals(0, request.attemptCount)
    }

    @Test
    fun `T29 cycle a provider retry still counts as an attempt and keeps the F45 backoff path`() = runTest {
        val dao: ExternalMetadataDao = mock()
        val vodDao: VodDao = mock()
        val seriesDao: SeriesDao = mock()
        val repository: ExternalMetadataRepository = mock()
        val request = ExternalHydrationRequestEntity("movie", 21, "MISSING_METADATA", 1, 1L, 1L, 2)
        whenever(dao.nextRequests(any(), any())).thenReturn(listOf(request), emptyList())
        whenever(vodDao.getStreamById(21)).thenReturn(vodRow(21, "provider-21"))
        whenever(repository.matchBatch(any())).thenReturn(
            listOf(ExternalMetadataMatchOutcome.Retry(9_000L, HydrationRetryReason.PROVIDER)),
        )

        ExternalMetadataHydrationWorker.drainQueue(dao, vodDao, seriesDao, repository) { 1_000L }

        val requeued = argumentCaptor<ExternalHydrationRequestEntity>()
        verify(dao).upsertRequest(requeued.capture())
        assertEquals(3, requeued.firstValue.attemptCount) // vraie tentative : le compteur avance
        assertEquals(10_000L, requeued.firstValue.nextAttemptAt) // Retry-After fournisseur prioritaire
        verify(dao, never()).deleteRequest(any(), any())
    }

    // ---------------------------------------------------------------------------------------
    // T29 cycle backfill P0-2 : plus de sommeil de 10-22 min tant que le catalogue n'a pas convergé.
    // ---------------------------------------------------------------------------------------

    @Test
    fun `T29 cycle a queue parked 20 minutes away still wakes up shortly while the catalog is incomplete`() = runTest {
        val dao: ExternalMetadataDao = mock()
        whenever(dao.earliestNextAttemptAt()).thenReturn(1_000L + 20 * 60_000L)

        val delay = ExternalMetadataHydrationWorker.nextWakeupDelayMillis(dao, 1_000L, catalogExhausted = false)

        assertEquals(ExternalMetadataHydrationWorker.BACKFILL_WAKEUP_MAX_DELAY_MILLIS, delay)
        assertTrue(delay!! < 20 * 60_000L)
    }

    @Test
    fun `T29 cycle a nearer deadline is never pushed back to the control wakeup`() = runTest {
        val dao: ExternalMetadataDao = mock()
        whenever(dao.earliestNextAttemptAt()).thenReturn(1_000L + 30_000L)

        val delay = ExternalMetadataHydrationWorker.nextWakeupDelayMillis(dao, 1_000L, catalogExhausted = false)

        assertEquals(30_000L, delay)
    }

    @Test
    fun `T29 cycle a converged catalog with an empty queue schedules nothing at all`() = runTest {
        val dao: ExternalMetadataDao = mock()
        whenever(dao.earliestNextAttemptAt()).thenReturn(null)

        assertNull(ExternalMetadataHydrationWorker.nextWakeupDelayMillis(dao, 1_000L, catalogExhausted = true))
        // Non convergé mais file vide : un seul réveil de contrôle, pas un sondage plus agressif.
        assertEquals(
            ExternalMetadataHydrationWorker.BACKFILL_WAKEUP_MAX_DELAY_MILLIS,
            ExternalMetadataHydrationWorker.nextWakeupDelayMillis(dao, 1_000L, catalogExhausted = false),
        )
    }

    @Test
    fun `T29 cycle a converged catalog keeps the real queue deadline, however far`() = runTest {
        val dao: ExternalMetadataDao = mock()
        whenever(dao.earliestNextAttemptAt()).thenReturn(1_000L + 6 * 3_600_000L)

        val delay = ExternalMetadataHydrationWorker.nextWakeupDelayMillis(dao, 1_000L, catalogExhausted = true)

        assertEquals(6 * 3_600_000L, delay)
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
