package com.cstv.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * État de suivi d'une série pour la détection de nouveaux épisodes (F12) : dernier épisode connu
 * du catalogue et dernier épisode ayant déjà fait l'objet d'une notification, par (profil, série).
 * T20 : la série est référencée par `mediaUid` (`kind = 'series'` sur `media_refs`) au lieu de
 * `seriesId` brut.
 */
@Entity(
    tableName = "series_watch_state",
    primaryKeys = ["profileId", "mediaUid"],
    foreignKeys = [
        ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = MediaRefEntity::class, parentColumns = ["mediaUid"], childColumns = ["mediaUid"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("mediaUid")],
)
data class SeriesWatchStateEntity(
    val profileId: Int,
    val mediaUid: Long,
    val lastKnownSeason: Int,
    val lastKnownEpisode: Int,
    /** -1 = jamais notifié. */
    val lastNotifiedSeason: Int,
    val lastNotifiedEpisode: Int,
    val updatedAt: Long,
)
