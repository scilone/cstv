package com.cstv.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/** Provider-neutral external identity. `externalId` is an opaque CSTV UUID. */
@Entity(tableName = "external_media", primaryKeys = ["externalId"], indices = [Index(value = ["kind"])])
data class ExternalMediaEntity(
    val externalId: String,
    val kind: String,
    val createdAt: Long,
    val refreshedAt: Long?,
    val refreshAfter: Long?,
)

/** Metadata shared by the app independently from the provider which produced it. */
@Entity(tableName = "external_movies", primaryKeys = ["externalId"])
data class ExternalMovieEntity(
    val externalId: String,
    val title: String,
    val originalTitle: String?,
    val overview: String?,
    val posterPath: String?,
    val backdropPath: String?,
    val releaseDate: String?,
    val runtimeMinutes: Int?,
    val ageRating: Int?,
)

@Entity(tableName = "external_series", primaryKeys = ["externalId"])
data class ExternalSeriesEntity(
    val externalId: String,
    val name: String,
    val originalName: String?,
    val overview: String?,
    val posterPath: String?,
    val backdropPath: String?,
    val firstAirDate: String?,
    val lastAirDate: String?,
    val inProduction: Boolean?,
    val ageRating: Int?,
)

/** Sous-ressources externes normalisées : elles ne dupliquent jamais les tables Xtream. */
@Entity(tableName = "external_seasons", primaryKeys = ["externalId", "seasonNumber"])
data class ExternalSeasonEntity(
    val externalId: String,
    val seasonNumber: Int,
    val name: String,
    val overview: String?,
    val posterPath: String?,
    val airDate: String?,
    val voteAverage: Double?,
    val refreshedAt: Long,
    val refreshAfter: Long?,
)

@Entity(tableName = "external_episodes", primaryKeys = ["externalId", "seasonNumber", "episodeNumber"])
data class ExternalEpisodeEntity(
    val externalId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val name: String,
    val overview: String?,
    val stillPath: String?,
    val airDate: String?,
    val runtimeMinutes: Int?,
    val voteAverage: Double?,
    val voteCount: Int?,
)

@Entity(tableName = "external_media_genres", primaryKeys = ["externalId", "name"])
data class ExternalGenreEntity(val externalId: String, val name: String)

@Entity(tableName = "external_media_keywords", primaryKeys = ["externalId", "name"])
data class ExternalKeywordEntity(val externalId: String, val name: String)

@Entity(tableName = "external_media_origin_countries", primaryKeys = ["externalId", "countryCode"])
data class ExternalOriginCountryEntity(val externalId: String, val countryCode: String)

@Entity(tableName = "external_series_episode_runtimes", primaryKeys = ["externalId", "minutes"])
data class ExternalEpisodeRuntimeEntity(val externalId: String, val minutes: Int)

@Entity(tableName = "external_alternative_titles", primaryKeys = ["externalId", "title"])
data class ExternalAlternativeTitleEntity(val externalId: String, val title: String)

@Entity(tableName = "external_recommendations", primaryKeys = ["externalId", "position"])
data class ExternalRecommendationEntity(val externalId: String, val position: Int, val recommendedExternalId: String)

@Entity(tableName = "external_videos", primaryKeys = ["externalId", "key"])
data class ExternalVideoEntity(
    val externalId: String,
    val key: String,
    val site: String,
    val type: String?,
    val name: String?,
    val official: Boolean,
)

/** Association to an Xtream row. Many versions may deliberately share one external work. */
@Entity(
    tableName = "external_media_links",
    primaryKeys = ["kind", "providerId"],
    indices = [Index(value = ["externalId"]), Index(value = ["linkKey"])]
)
data class ExternalMediaLinkEntity(
    val kind: String,
    val providerId: Int,
    val externalId: String?,
    val linkKey: String?,
    val confidence: Int?,
    val matchMethod: String?,
    val matchVersion: Int?,
    val matchedAt: Long?,
    val lastMatchAttemptAt: Long?,
    val retryAfter: Long?,
)

@Entity(
    tableName = "external_hydration_queue",
    primaryKeys = ["kind", "providerId"],
    indices = [Index(value = ["priority", "nextAttemptAt"])]
)
data class ExternalHydrationRequestEntity(
    val kind: String,
    val providerId: Int,
    val reason: String,
    val priority: Int,
    val createdAt: Long,
    val nextAttemptAt: Long,
    val attemptCount: Int,
)
