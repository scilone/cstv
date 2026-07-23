package com.cstv.app.domain.model

/** Identité du titre dont l'aperçu est demandé : jamais l'index volatile du pager. */
sealed interface TrailerMedia {
    val catalogId: Int
    val tmdbId: Int

    data class Movie(override val catalogId: Int, override val tmdbId: Int) : TrailerMedia
    data class Series(override val catalogId: Int, override val tmdbId: Int) : TrailerMedia
}

sealed interface TrailerSource {
    data class YouTube(val videoId: String) : TrailerSource
}

data class TrailerPreview(
    val media: TrailerMedia,
    val source: TrailerSource
)
