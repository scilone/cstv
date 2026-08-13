package com.cstv.app.domain.model

/** Stable, account-scoped reference to a catalog target. */
enum class MediaKind(val storageValue: String) {
    LIVE("live"),
    MOVIE("movie"),
    SERIES("series"),
    EPISODE("episode");

    companion object {
        fun fromStorage(value: String): MediaKind? = entries.firstOrNull { it.storageValue == value }
    }
}

/** Media3 identifiers are derived, never persisted as a second identity. */
object DownloadContentId {
    fun of(kind: MediaKind, providerId: Int): String {
        require(kind == MediaKind.MOVIE || kind == MediaKind.EPISODE)
        return "${kind.storageValue}_$providerId"
    }

    fun parse(value: String): Pair<MediaKind, Int>? {
        val separator = value.indexOf('_')
        if (separator <= 0 || separator == value.lastIndex) return null
        val kind = MediaKind.fromStorage(value.substring(0, separator)) ?: return null
        if (kind != MediaKind.MOVIE && kind != MediaKind.EPISODE) return null
        return value.substring(separator + 1).toIntOrNull()?.takeIf { it >= 0 }?.let { kind to it }
    }
}
