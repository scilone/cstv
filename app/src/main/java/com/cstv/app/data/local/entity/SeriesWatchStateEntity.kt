package com.cstv.app.data.local.entity

import androidx.room.Entity

/**
 * État de suivi d'une série pour la détection de nouveaux épisodes (F12) :
 * dernier épisode connu du catalogue et dernier épisode ayant déjà fait
 * l'objet d'une notification, par (profil, série).
 */
@Entity(tableName = "series_watch_state", primaryKeys = ["profileId", "seriesId"])
data class SeriesWatchStateEntity(
    val profileId: Int,
    val seriesId: Int,
    val lastKnownSeason: Int,
    val lastKnownEpisode: Int,
    /** -1 = jamais notifié. */
    val lastNotifiedSeason: Int,
    val lastNotifiedEpisode: Int,
    val updatedAt: Long
)
