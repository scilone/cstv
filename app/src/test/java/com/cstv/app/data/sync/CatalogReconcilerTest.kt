package com.cstv.app.data.sync

import com.cstv.app.data.local.dao.DownloadDao
import com.cstv.app.data.local.dao.MediaRefDao
import com.cstv.app.data.local.dao.OrphanedDownloadRow
import com.cstv.app.data.local.dao.TrailerCacheDao
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

/**
 * T20 §4.5 : la réconciliation ne s'exécute jamais en dehors de l'ordre
 * imposé (`ORPHANED` → retrait media3 → suppression de la ligne), et un
 * échec à n'importe quelle étape laisse la ligne rejouable plutôt que de
 * prétendre à un nettoyage qui n'a pas eu lieu.
 */
class CatalogReconcilerTest {
    @get:Rule val globalTimeout: Timeout = Timeout.seconds(60)

    private val accountKey = "account-key"

    @Mock private lateinit var downloadDao: DownloadDao
    @Mock private lateinit var trailerCacheDao: TrailerCacheDao
    @Mock private lateinit var mediaRefDao: MediaRefDao

    private lateinit var removedContentIds: MutableList<String>
    private lateinit var reconciler: CatalogReconciler

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        removedContentIds = mutableListOf()
        reconciler = CatalogReconciler(downloadDao, trailerCacheDao, mediaRefDao, DownloadContentRemover { removedContentIds.add(it) })
    }

    @Test
    fun `always purges orphaned trailer cache, independent of any download`() = runTest {
        whenever(downloadDao.findOrphaned(accountKey)).thenReturn(emptyList())

        reconciler.reconcile(accountKey)

        verify(trailerCacheDao).deleteOrphaned()
    }

    @Test
    fun `an orphaned download is marked, removed from media3, then deleted, in that order`() = runTest {
        whenever(downloadDao.findOrphaned(accountKey)).thenReturn(
            listOf(OrphanedDownloadRow(mediaUid = 42L, kind = "movie", providerId = 815))
        )

        reconciler.reconcile(accountKey)

        inOrder(downloadDao) {
            verify(downloadDao).markOrphaned(42L)
            verify(downloadDao).deleteByMediaUid(42L)
        }
        assert(removedContentIds == listOf("movie_815")) { "attendu movie_815, obtenu $removedContentIds" }
        verify(mediaRefDao).purgeUnreferenced()
    }

    @Test
    fun `an episode is removed via its media3 content id, not the movie one`() = runTest {
        whenever(downloadDao.findOrphaned(accountKey)).thenReturn(
            listOf(OrphanedDownloadRow(mediaUid = 7L, kind = "episode", providerId = 99213))
        )

        reconciler.reconcile(accountKey)

        assert(removedContentIds == listOf("episode_99213")) { "attendu episode_99213, obtenu $removedContentIds" }
    }

    @Test
    fun `a media3 removal failure leaves the row ORPHANED, never deleted, never marked clean`() = runTest {
        whenever(downloadDao.findOrphaned(accountKey)).thenReturn(
            listOf(OrphanedDownloadRow(mediaUid = 42L, kind = "movie", providerId = 815))
        )
        reconciler = CatalogReconciler(
            downloadDao, trailerCacheDao, mediaRefDao,
            DownloadContentRemover { throw RuntimeException("media3 indisponible") },
        )

        reconciler.reconcile(accountKey)

        verify(downloadDao).markOrphaned(42L)
        verify(downloadDao, never()).deleteByMediaUid(org.mockito.kotlin.any())
    }

    @Test
    fun `an unresolvable kind is skipped rather than guessed`() = runTest {
        whenever(downloadDao.findOrphaned(accountKey)).thenReturn(
            listOf(OrphanedDownloadRow(mediaUid = 1L, kind = "live", providerId = 1))
        )

        reconciler.reconcile(accountKey)

        verify(downloadDao, never()).markOrphaned(org.mockito.kotlin.any())
        assert(removedContentIds.isEmpty())
    }

    @Test
    fun `a trailer cache purge failure does not prevent download reconciliation`() = runTest {
        whenever(trailerCacheDao.deleteOrphaned()).doThrow(RuntimeException("db locked"))
        whenever(downloadDao.findOrphaned(accountKey)).thenReturn(
            listOf(OrphanedDownloadRow(mediaUid = 42L, kind = "movie", providerId = 815))
        )

        reconciler.reconcile(accountKey)

        verify(downloadDao).deleteByMediaUid(42L)
    }

    @Test
    fun `no orphaned download touches neither media3 nor media_refs`() = runTest {
        whenever(downloadDao.findOrphaned(accountKey)).thenReturn(emptyList())

        reconciler.reconcile(accountKey)

        assert(removedContentIds.isEmpty())
        verifyNoInteractions(mediaRefDao)
    }

    // T20-R3: `findOrphaned` used to exclude rows already `ORPHANED`, so a row that failed once
    // was never selected again -- it stayed forever half-cleaned. The fix is in the DAO's SQL
    // (`DownloadDao.findOrphaned`, now `dm.status = 'ORPHANED' OR ...`); these tests pin down the
    // reconciler's side of the contract -- that a row the DAO *does* resurface is genuinely
    // retried, and that a failure at either remaining step (media3, then Room) still leaves the row
    // rejoinable instead of silently losing it.

    @Test
    fun `a row that failed media3 removal on one cycle is retried and cleaned up on the next`() = runTest {
        val row = OrphanedDownloadRow(mediaUid = 42L, kind = "movie", providerId = 815)
        var failFirstAttempt = true
        val flakyRemover = DownloadContentRemover { contentId ->
            if (failFirstAttempt) { failFirstAttempt = false; throw RuntimeException("media3 indisponible") }
            removedContentIds.add(contentId)
        }
        reconciler = CatalogReconciler(downloadDao, trailerCacheDao, mediaRefDao, flakyRemover)
        whenever(downloadDao.findOrphaned(accountKey)).thenReturn(listOf(row))

        // Cycle 1: media3 removal fails, the row must not be deleted.
        reconciler.reconcile(accountKey)
        verify(downloadDao, never()).deleteByMediaUid(42L)

        // Cycle 2: the DAO resurfaces the still-ORPHANED row (contract asserted by the fixed SQL,
        // simulated here since the DAO itself is mocked) and the retry now succeeds end to end.
        reconciler.reconcile(accountKey)
        verify(downloadDao).deleteByMediaUid(42L)
        assert(removedContentIds == listOf("movie_815")) { "attendu un seul retrait réussi movie_815, obtenu $removedContentIds" }
    }

    @Test
    fun `a Room deletion failure after a successful media3 removal leaves the row ORPHANED and does not crash`() = runTest {
        whenever(downloadDao.findOrphaned(accountKey)).thenReturn(
            listOf(OrphanedDownloadRow(mediaUid = 42L, kind = "movie", providerId = 815))
        )
        whenever(downloadDao.deleteByMediaUid(42L)).doThrow(RuntimeException("db locked"))

        reconciler.reconcile(accountKey)

        verify(downloadDao).markOrphaned(42L)
        // media3 removal itself succeeded (it ran before the Room failure) -- only the Room
        // deletion that follows it failed, so the row is retried next cycle rather than lost.
        assert(removedContentIds == listOf("movie_815")) { "attendu retrait media3 déjà effectué, obtenu $removedContentIds" }
        verify(downloadDao).deleteByMediaUid(42L)
    }
}
