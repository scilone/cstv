package com.cstv.app.domain.model

/** Identité du titre dont l'aperçu est demandé : jamais l'index volatile du pager. */
sealed interface TrailerMedia {
    val catalogId: Int
    val tmdbId: Int?
    val title: String?
    val releaseYear: Int?

    data class Movie(override val catalogId: Int, override val tmdbId: Int? = null, override val title: String? = null, override val releaseYear: Int? = null) : TrailerMedia
    data class Series(override val catalogId: Int, override val tmdbId: Int? = null, override val title: String? = null, override val releaseYear: Int? = null) : TrailerMedia
}

sealed interface TrailerSource {
    data class YouTube(val videoId: String) : TrailerSource
}

data class TrailerPreview(
    val media: TrailerMedia,
    val source: TrailerSource
)
