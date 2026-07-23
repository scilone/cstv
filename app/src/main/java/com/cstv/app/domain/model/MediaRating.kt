package com.cstv.app.domain.model

enum class RatedMediaType(val storageValue: String) {
    MOVIE("movie"),
    SERIES("series")
}

enum class MediaRatingValue(val storageValue: Int) {
    LIKE(1),
    DISLIKE(-1)
}

data class MediaRating(
    val mediaId: Int,
    val mediaType: RatedMediaType,
    val value: MediaRatingValue
)
