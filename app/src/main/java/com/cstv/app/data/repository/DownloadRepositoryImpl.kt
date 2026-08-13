package com.cstv.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.cstv.app.data.download.IptvDownloadService
import com.cstv.app.data.download.OfflineDownloadUtil
import com.cstv.app.data.local.dao.DownloadDao
import com.cstv.app.data.local.dao.DownloadListRow
import com.cstv.app.data.local.dao.MediaRefDao
import com.cstv.app.data.local.entity.DownloadedMediaEntity
import com.cstv.app.data.local.storage.CurrentAccountKeyProvider
import com.cstv.app.domain.model.DownloadContentId
import com.cstv.app.domain.model.DownloadRequestData
import com.cstv.app.domain.model.DownloadStatus
import com.cstv.app.domain.model.DownloadedItem
import com.cstv.app.domain.model.EpisodeLabel
import com.cstv.app.domain.repository.DownloadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Implémente [DownloadRepository] par-dessus le `DownloadManager` media3.
 *
 * Room (`downloaded_media`) est la **source de vérité de l'UI** : à chaque
 * changement d'état media3 (listener) et à intervalle régulier (poll de
 * progression, media3 ne pousse pas la progression octet par octet), les lignes
 * Room sont mises à jour. La couche présentation n'observe que Room.
 *
 * T20 : `contentId` ("movie_123" / "episode_456", clé media3) n'est plus stocké ; il est retrouvé
 * via [DownloadContentId.parse] pour router chaque événement media3 vers son `(kind, providerId)`,
 * et reconstruit via [DownloadContentId.of] pour l'affichage. Titre/jaquette/extension viennent du
 * catalogue courant.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class DownloadRepositoryImpl(
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val mediaRefDao: MediaRefDao,
    private val accountKeyProvider: CurrentAccountKeyProvider
) : DownloadRepository {

    private val appContext = context.applicationContext
    private val downloadManager: DownloadManager = OfflineDownloadUtil.getDownloadManager(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        downloadManager.addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?
            ) {
                scope.launch { syncDownload(download) }
            }

            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                scope.launch {
                    val (kind, providerId) = DownloadContentId.parse(download.request.id) ?: return@launch
                    downloadDao.delete(accountKeyProvider.current(), kind.storageValue, providerId)
                    mediaRefDao.purgeUnreferenced()
                }
            }
        })
        startProgressPoller()
    }

    // media3 ne notifie pas la progression en continu : on interroge les
    // téléchargements actifs à intervalle court, cadence relâchée quand idle.
    private fun startProgressPoller() {
        scope.launch {
            while (true) {
                val current = downloadManager.currentDownloads
                current.forEach { syncDownload(it) }
                delay(if (current.isEmpty()) 3000L else 1000L)
            }
        }
    }

    private suspend fun syncDownload(download: Download) {
        val (kind, providerId) = DownloadContentId.parse(download.request.id) ?: return
        val status = mapStatus(download.state)
        val percent = download.percentDownloaded.let {
            if (it.isNaN() || it < 0f) 0 else it.toInt().coerceIn(0, 100)
        }
        val bytes = download.bytesDownloaded
        val total = if (download.contentLength > 0) {
            download.contentLength
        } else if (percent in 1..99 && bytes > 0) {
            (bytes * 100L / percent)
        } else {
            bytes
        }
        val accountKey = accountKeyProvider.current()
        // N'écrit que si la ligne existe déjà (métadonnées créées au démarrage).
        if (downloadDao.getByRef(accountKey, kind.storageValue, providerId) != null) {
            downloadDao.updateProgress(accountKey, kind.storageValue, providerId, status.name, percent, bytes, total)
        }
    }

    private fun mapStatus(state: Int): DownloadStatus = when (state) {
        Download.STATE_QUEUED -> DownloadStatus.QUEUED
        Download.STATE_DOWNLOADING -> DownloadStatus.DOWNLOADING
        Download.STATE_COMPLETED -> DownloadStatus.COMPLETED
        Download.STATE_FAILED -> DownloadStatus.FAILED
        Download.STATE_STOPPED -> DownloadStatus.PAUSED
        Download.STATE_REMOVING -> DownloadStatus.REMOVING
        Download.STATE_RESTARTING -> DownloadStatus.DOWNLOADING
        else -> DownloadStatus.QUEUED
    }

    override fun observeDownloads(): Flow<List<DownloadedItem>> {
        return downloadDao.observeAll(accountKeyProvider.current()).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun startDownload(data: DownloadRequestData) {
        val accountKey = accountKeyProvider.current()
        val mediaUid = mediaRefDao.resolve(accountKey, data.type, data.streamId)
        // Métadonnées d'abord (source de vérité UI), puis lancement media3.
        downloadDao.upsert(
            DownloadedMediaEntity(
                mediaUid = mediaUid,
                status = DownloadStatus.QUEUED.name,
                percent = 0,
                bytesDownloaded = 0L,
                totalBytes = 0L,
                createdAt = System.currentTimeMillis()
            )
        )
        val request = DownloadRequest.Builder(data.contentId, Uri.parse(data.url))
            .setCustomCacheKey(data.contentId)
            .build()
        DownloadService.sendAddDownload(
            appContext,
            IptvDownloadService::class.java,
            request,
            /* foreground= */ false
        )
    }

    override suspend fun removeDownload(contentId: String) {
        DownloadService.sendRemoveDownload(
            appContext,
            IptvDownloadService::class.java,
            contentId,
            /* foreground= */ false
        )
        val (kind, providerId) = DownloadContentId.parse(contentId) ?: return
        downloadDao.delete(accountKeyProvider.current(), kind.storageValue, providerId)
        mediaRefDao.purgeUnreferenced()
    }

    override suspend fun getUsedBytes(): Long = downloadDao.getTotalBytesDownloaded()

    private fun DownloadListRow.toDomain(): DownloadedItem {
        val isEpisode = kind == com.cstv.app.domain.model.MediaKind.EPISODE.storageValue
        val label = if (isEpisode) EpisodeLabel.format(seasonNum, episodeNum) else null
        val displayTitle = if (isEpisode && label != null) "${title.orEmpty()} — $label" else title.orEmpty()
        return DownloadedItem(
            contentId = DownloadContentId.of(
                if (isEpisode) com.cstv.app.domain.model.MediaKind.EPISODE else com.cstv.app.domain.model.MediaKind.MOVIE,
                providerId
            ),
            type = kind,
            streamId = providerId,
            seriesId = seriesId,
            seasonNum = seasonNum,
            episodeNum = episodeNum,
            title = displayTitle,
            subtitle = if (isEpisode) episodeTitle else null,
            coverUrl = coverUrl,
            containerExtension = containerExtension ?: DEFAULT_CONTAINER_EXTENSION,
            status = runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.QUEUED),
            percent = percent,
            bytesDownloaded = bytesDownloaded,
            totalBytes = totalBytes
        )
    }

    private companion object {
        const val DEFAULT_CONTAINER_EXTENSION = "mp4"
    }
}
