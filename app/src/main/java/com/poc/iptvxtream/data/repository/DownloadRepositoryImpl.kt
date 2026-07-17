package com.poc.iptvxtream.data.repository

import android.content.Context
import android.net.Uri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.poc.iptvxtream.data.download.IptvDownloadService
import com.poc.iptvxtream.data.download.OfflineDownloadUtil
import com.poc.iptvxtream.data.local.dao.DownloadDao
import com.poc.iptvxtream.data.local.entity.DownloadedMediaEntity
import com.poc.iptvxtream.domain.model.DownloadRequestData
import com.poc.iptvxtream.domain.model.DownloadStatus
import com.poc.iptvxtream.domain.model.DownloadedItem
import com.poc.iptvxtream.domain.repository.DownloadRepository
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
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class DownloadRepositoryImpl(
    private val context: Context,
    private val downloadDao: DownloadDao
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
                scope.launch { downloadDao.delete(download.request.id) }
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
        // N'écrit que si la ligne existe déjà (métadonnées créées au démarrage).
        if (downloadDao.getByContentId(download.request.id) != null) {
            downloadDao.updateProgress(download.request.id, status.name, percent, bytes, total)
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
        return downloadDao.observeAll().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun startDownload(data: DownloadRequestData) {
        // Métadonnées d'abord (source de vérité UI), puis lancement media3.
        downloadDao.upsert(
            DownloadedMediaEntity(
                contentId = data.contentId,
                type = data.type,
                streamId = data.streamId,
                seriesId = data.seriesId,
                seasonNum = data.seasonNum,
                episodeNum = data.episodeNum,
                title = data.title,
                subtitle = data.subtitle,
                coverUrl = data.coverUrl,
                containerExtension = data.containerExtension,
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
        downloadDao.delete(contentId)
    }

    override suspend fun getUsedBytes(): Long = downloadDao.getTotalBytesDownloaded()

    private fun DownloadedMediaEntity.toDomain() = DownloadedItem(
        contentId = contentId,
        type = type,
        streamId = streamId,
        seriesId = seriesId,
        seasonNum = seasonNum,
        episodeNum = episodeNum,
        title = title,
        subtitle = subtitle,
        coverUrl = coverUrl,
        containerExtension = containerExtension,
        status = runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.QUEUED),
        percent = percent,
        bytesDownloaded = bytesDownloaded,
        totalBytes = totalBytes
    )
}
