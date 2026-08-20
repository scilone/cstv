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

/**
 * T20-R2: `ratings`, `track-preferences`, `series-watch-state` and `category-preferences` are
 * namespaces the PO explicitly kept at v1 -- unlike [FavoriteWire]/[PlaybackWire]/
 * [RecentlyWatchedWire], their JSON body must stay byte-for-byte what the pre-T20 entity produced
 * (`gson.toJsonTree(entity).withoutFields("profileId")`), not just what the map key encodes. The
 * fields below mirror the wire key on purpose -- dropping them would silently publish a second,
 * undeclared document shape under the same `schemaVersion = 1` (see review finding T20-R2).
 */
data class RatingWire(val mediaType: String, val mediaId: Int, val value: Int)

data class TrackPreferenceWire(val mediaType: String, val mediaId: Int, val audioLang: String?, val subtitleLang: String?)

data class SeriesWatchStateWire(
    val seriesId: Int,
    val lastKnownSeason: Int,
    val lastKnownEpisode: Int,
    val lastNotifiedSeason: Int,
    val lastNotifiedEpisode: Int,
    val updatedAt: Long,
)

data class CategoryPreferenceWire(val categoryId: String, val type: String, val hidden: Boolean, val sortOrder: Int?)

data class ProfilePreferencesWire(val autoPlayNextEpisode: Boolean)

/** F45 (évolution F44) : autorisation permanente accordée par PIN à un profil bridé. */
data class ParentalAuthorizationWire(val grantedAt: Long)
