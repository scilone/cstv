package com.cstv.app.domain.notification

import com.cstv.app.domain.model.EpisodeRef

/**
 * Alerte de nouveaux épisodes (F12). Interface mockable : le use case
 * d'orchestration n'a pas à connaître Android (`NotificationManagerCompat`,
 * canal, `PendingIntent`).
 */
interface NewEpisodeNotifier {
    fun notifyNewEpisodes(profileId: Int, seriesId: Int, seriesName: String, latest: EpisodeRef)
}
