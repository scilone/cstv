package com.cstv.app.data.cloudsync.wire

/**
 * T20 (§4.6): transport DTOs for cloud sync, decoupled from the Room entities. Only the business
 * value proper to each state lives here -- no catalogue metadata (title, cover, category, ...),
 * no `profileId`. The object key in the wire document is always `"kind:providerId"` (e.g.
 * `"movie:815"`, `"episode:99213"`, `"live:1042"`), which is also what disambiguates a movie from
 * an episode sharing the same numeric id -- the ambiguity the old bare `streamId` key had.
 */
data class FavoriteWire(val addedAt: Long)

data class PlaybackWire(val positionMs: Long, val durationMs: Long, val lastAccessedAt: Long)

data class RecentlyWatchedWire(val watchedAt: Long)

/** Unchanged v1 shape (namespace not versioned): [ratedMediaType]/[ratedMediaId] mirror the wire
 *  key so a decoded object stays self-describing even though the key already carries them. */
data class RatingWire(val value: Int)

data class TrackPreferenceWire(val audioLang: String?, val subtitleLang: String?)

data class SeriesWatchStateWire(
    val lastKnownSeason: Int,
    val lastKnownEpisode: Int,
    val lastNotifiedSeason: Int,
    val lastNotifiedEpisode: Int,
    val updatedAt: Long,
)

data class CategoryPreferenceWire(val hidden: Boolean, val sortOrder: Int?)
