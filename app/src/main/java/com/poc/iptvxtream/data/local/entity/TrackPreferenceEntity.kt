package com.poc.iptvxtream.data.local.entity

import androidx.room.Entity

/**
 * Préférence de langue audio / sous-titres mémorisée par média (Phase 29),
 * spécifique au profil actif (Phase 27).
 *
 * `mediaType` : "movie" (clé = streamId du film) ou "series" (clé = seriesId
 * de la série, commune à tous ses épisodes). `subtitleLang` peut valoir "none"
 * pour un choix explicite « pas de sous-titres ».
 */
@Entity(tableName = "track_preferences", primaryKeys = ["profileId", "mediaType", "mediaId"])
data class TrackPreferenceEntity(
    val profileId: Int,
    val mediaType: String,
    val mediaId: Int,
    val audioLang: String?,
    val subtitleLang: String?
)
