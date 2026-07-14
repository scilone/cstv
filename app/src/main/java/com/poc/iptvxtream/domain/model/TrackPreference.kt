package com.poc.iptvxtream.domain.model

/**
 * Préférence de pistes mémorisée pour un média (Phase 29). `subtitleLang` peut
 * valoir "none" pour un choix explicite « sans sous-titres ».
 */
data class TrackPreference(
    val audioLang: String?,
    val subtitleLang: String?
)

enum class MediaType(val value: String) {
    MOVIE("movie"),
    SERIES("series")
}
