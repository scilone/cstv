package com.cstv.app.data.download

import android.app.Notification
import androidx.media3.common.util.NotificationUtil
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.cstv.app.R

/**
 * Foreground service media3 qui exécute les téléchargements et affiche la
 * notification de progression obligatoire (Android l'impose pour tout
 * téléchargement long). Partage le `DownloadManager`/`Cache` unique via
 * [OfflineDownloadUtil].
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class IptvDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    OfflineDownloadUtil.DOWNLOAD_NOTIFICATION_CHANNEL_ID,
    R.string.download_channel_name,
    R.string.download_channel_description
) {

    override fun getDownloadManager(): DownloadManager =
        OfflineDownloadUtil.getDownloadManager(this)

    // Pas de reprise planifiée après mort du process (POC) : reprise manuelle.
    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(
        downloads: List<Download>,
        notMetRequirements: Int
    ): Notification {
        return OfflineDownloadUtil.getDownloadNotificationHelper(this)
            .buildProgressNotification(
                this,
                android.R.drawable.stat_sys_download,
                null,
                null,
                downloads,
                notMetRequirements
            )
    }

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 4211

        // Rappel utilisé par NotificationUtil pour créer le canal côté OS.
        const val NOTIFICATION_IMPORTANCE = NotificationUtil.IMPORTANCE_LOW
    }
}
