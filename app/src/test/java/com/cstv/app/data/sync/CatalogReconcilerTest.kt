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
}
