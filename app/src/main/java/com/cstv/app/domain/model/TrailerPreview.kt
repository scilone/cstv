package com.cstv.app.domain.model

/** Identité du titre dont l'aperçu est demandé : jamais l'index volatile du pager. */
sealed interface TrailerMedia {
    val catalogId: Int
    val externalId: String?

    data class Movie(override val catalogId: Int, override val externalId: String? = null) : TrailerMedia {
        @Deprecated("Tests and legacy cache only; production receives externalId from CSTV")
        constructor(catalogId: Int, legacyId: Int?) : this(catalogId, legacyId?.let { "movie:$it" })
    }
    data class Series(override val catalogId: Int, override val externalId: String? = null) : TrailerMedia {
        @Deprecated("Tests and legacy cache only; production receives externalId from CSTV")
        constructor(catalogId: Int, legacyId: Int?) : this(catalogId, legacyId?.let { "series:$it" })
    }
}

sealed interface TrailerSource {
    data class YouTube(val videoId: String) : TrailerSource
}

data class TrailerPreview(
    val media: TrailerMedia,
    val source: TrailerSource
)
