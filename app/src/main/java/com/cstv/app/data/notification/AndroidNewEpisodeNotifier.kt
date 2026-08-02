package com.cstv.app.data.notification

import android.app.PendingIntent
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cstv.app.MainActivity
import com.cstv.app.R
import com.cstv.app.di.IptvLog
import com.cstv.app.domain.model.EpisodeRef
import com.cstv.app.domain.notification.NewEpisodeNotifier
import com.cstv.app.presentation.navigation.SeriesDeepLink
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "NEWEP"
private const val CHANNEL_ID = "new_episodes"

/**
 * Notification Android de nouveaux épisodes (F12). Aucune donnée Xtream
 * (identifiants, URL de flux) ni nom de profil dans le contenu visible : seuls
 * `profileId`/`seriesId` transitent, dans les extras du `PendingIntent`.
 */
@Singleton
class AndroidNewEpisodeNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) : NewEpisodeNotifier {

    override fun notifyNewEpisodes(profileId: Int, seriesId: Int, seriesName: String, latest: EpisodeRef) {
        try {
            val notificationManager = NotificationManagerCompat.from(context)
            if (!shouldShowNotification(isTvDevice(), notificationManager.areNotificationsEnabled())) return

            ensureChannel(notificationManager)

            val notificationId = buildNotificationId(profileId, seriesId)
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra(SeriesDeepLink.EXTRA_SERIES_ID, seriesId)
                putExtra(SeriesDeepLink.EXTRA_PROFILE_ID, profileId)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(context.getString(R.string.new_episodes_notification_title))
                .setContentText(seriesName)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(notificationId, notification)
        } catch (e: SecurityException) {
            IptvLog.e(TAG, "Notification refusée par le système pour la série $seriesId", e)
        } catch (e: Exception) {
            IptvLog.e(TAG, "Échec d'affichage de la notification pour la série $seriesId", e)
        }
    }

    private fun ensureChannel(notificationManager: NotificationManagerCompat) {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.new_episodes_channel_name))
            .setDescription(context.getString(R.string.new_episodes_channel_description))
            .build()
        notificationManager.createNotificationChannel(channel)
    }

    private fun isTvDevice(): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    companion object {
        /**
         * Gardes métier F12 extraites en logique pure testable en JVM (F12-R2) :
         * jamais de notification sur Android TV, jamais si l'utilisateur a
         * désactivé les notifications au niveau système.
         */
        internal fun shouldShowNotification(isTvDevice: Boolean, notificationsEnabled: Boolean): Boolean =
            !isTvDevice && notificationsEnabled

        /**
         * Unicité du `PendingIntent` par couple (profil, série) (F12-R2) :
         * deux séries ou deux profils différents ne doivent jamais partager le
         * même `notificationId`, sous peine d'écraser la notification/l'intent
         * de l'un avec celui de l'autre.
         */
        internal fun buildNotificationId(profileId: Int, seriesId: Int): Int =
            ("newep:$profileId:$seriesId").hashCode()
    }
}
