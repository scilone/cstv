package com.cstv.app.domain.model

/** État de suivi persisté d'une série pour un profil (F12). */
data class SeriesWatchState(
    val profileId: Int,
    val seriesId: Int,
    val lastKnown: EpisodeRef,
    /** null = jamais notifié. */
    val lastNotified: EpisodeRef?
)
