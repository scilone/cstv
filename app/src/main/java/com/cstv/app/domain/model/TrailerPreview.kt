package com.cstv.app.domain.model

/** Identité du titre dont l'aperçu est demandé : jamais l'index volatile du pager. */
sealed interface TrailerMedia {
    val catalogId: Int
    val canonicalId: String?

    data class Movie(override val catalogId: Int, override val canonicalId: String? = null) : TrailerMedia {
        @Deprecated("Tests and legacy cache only; production receives canonicalId from CSTV")
        constructor(catalogId: Int, tmdbId: Int?) : this(catalogId, tmdbId?.let { "movie:$it" })
    }
    data class Series(override val catalogId: Int, override val canonicalId: String? = null) : TrailerMedia {
        @Deprecated("Tests and legacy cache only; production receives canonicalId from CSTV")
        constructor(catalogId: Int, tmdbId: Int?) : this(catalogId, tmdbId?.let { "series:$it" })
    }
}

sealed interface TrailerSource {
    data class YouTube(val videoId: String) : TrailerSource
}

data class TrailerPreview(
    val media: TrailerMedia,
    val source: TrailerSource
)
