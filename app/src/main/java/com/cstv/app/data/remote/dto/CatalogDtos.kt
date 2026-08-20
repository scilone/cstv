package com.cstv.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class CatalogItemsResponseDto(val items: List<CatalogItemDto>? = null)
data class CatalogMatchHintsDto(
    val director: String? = null,
    val actors: List<String>? = null,
    val genres: List<String>? = null,
    val runtimeMinutes: Int? = null,
    val youtubeTrailerKey: String? = null,
)
data class CatalogMatchRequestDto(
    val kind: String,
    val title: String,
    val year: Int? = null,
    val locale: String = "fr-FR",
    val hints: CatalogMatchHintsDto? = null,
)
data class CatalogMatchQualityDto(val confidence: Int? = null, val method: String? = null, val version: Int? = null)
data class CatalogMatchResponseDto(val status: String? = null, val match: CatalogMatchQualityDto? = null, val item: CatalogItemDto? = null, val cache: CatalogCacheDto? = null)
data class CatalogVideosResponseDto(val items: List<CatalogVideoDto>? = null, val cache: CatalogCacheDto? = null)
data class CatalogCacheDto(val stale: Boolean? = null, @SerializedName("updatedAt") val updatedAt: String? = null, @SerializedName("refreshAfter") val refreshAfter: String? = null)
data class CatalogItemDto(
    @SerializedName("externalId") val externalId: String? = null,
    val id: String? = null, val kind: String? = null, val title: String? = null,
    @SerializedName("originalTitle") val originalTitle: String? = null,
    @SerializedName("releaseYear") val releaseYear: Int? = null,
    val overview: String? = null, val rating: Double? = null,
    @SerializedName("posterUrl") val posterUrl: String? = null,
    @SerializedName("backdropUrl") val backdropUrl: String? = null,
    @SerializedName("ageRatingFr") val ageRatingFr: Int? = null,
    @SerializedName("ageRating") val ageRating: Int? = null,
    val adult: Boolean? = null,
    @SerializedName("originalLanguage") val originalLanguage: String? = null,
    val genres: List<String>? = null,
    @SerializedName("originCountries") val originCountries: List<String>? = null,
    val keywords: List<String>? = null,
    @SerializedName("alternativeTitles") val alternativeTitles: List<String>? = null,
    val recommendations: List<String>? = null,
    val videos: List<CatalogVideoDto>? = null,
    @SerializedName("runtimeMinutes") val runtimeMinutes: Int? = null,
    val status: String? = null,
    val tagline: String? = null,
    @SerializedName("voteCount") val voteCount: Int? = null,
    @SerializedName("firstAirDate") val firstAirDate: String? = null,
    @SerializedName("lastAirDate") val lastAirDate: String? = null,
    @SerializedName("numberOfEpisodes") val numberOfEpisodes: Int? = null,
    @SerializedName("numberOfSeasons") val numberOfSeasons: Int? = null,
    @SerializedName("episodeRunTimes") val episodeRunTimes: List<Int>? = null,
    @SerializedName("inProduction") val inProduction: Boolean? = null,
    @SerializedName("nextEpisodeToAir") val nextEpisodeToAir: String? = null,
)
data class CatalogVideoDto(val site: String? = null, val key: String? = null, val type: String? = null, val official: Boolean? = null, val name: String? = null)
data class CatalogRecommendationDto(@SerializedName("externalId") val externalId: String? = null)
data class CatalogRecommendationsResponseDto(val items: List<CatalogRecommendationDto>? = null)
data class CatalogEpisodeDto(
    val episodeNumber: Int? = null,
    val name: String? = null,
    val overview: String? = null,
    val stillUrl: String? = null,
    val airDate: String? = null,
    val runtimeMinutes: Int? = null,
    val voteAverage: Double? = null,
    val voteCount: Int? = null,
)
data class CatalogSeasonDto(
    val seasonNumber: Int? = null,
    val name: String? = null,
    val overview: String? = null,
    val posterUrl: String? = null,
    val airDate: String? = null,
    val voteAverage: Double? = null,
    val episodes: List<CatalogEpisodeDto>? = null,
)
