package com.cstv.app.data.remote.dto

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class SeriesInfoResponseDto(
    @SerializedName("seasons") val seasons: List<SeriesSeasonDto>?,
    @SerializedName("episodes") val episodes: Map<String, List<SeriesEpisodeDto>>?,
    @SerializedName("info") val info: SeriesInfoMetadataDto?
)

data class SeriesInfoMetadataDto(
    @SerializedName("name") val name: String?,
    @SerializedName("cover") val cover: String?,
    @SerializedName("plot") val plot: String?,
    @SerializedName("cast") val cast: JsonElement?,
    @SerializedName("actors") val actors: JsonElement?,
    @SerializedName("director") val director: String?,
    @SerializedName("releaseDate") val releaseDate: String?,
    @SerializedName("releasedate") val releaseDate2: String?,
    @SerializedName("genre") val genre: String?,
    @SerializedName("rating") val rating: String?,
    @SerializedName("rating_5") val rating5: String?
)

data class SeriesSeasonDto(
    @SerializedName("air_date") val airDate: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("episode_count") val episodeCount: Int?,
    @SerializedName("season_number") val seasonNumber: Int?,
    @SerializedName("cover") val cover: String?
)

data class SeriesEpisodeDto(
    @SerializedName("id") val id: String?,
    @SerializedName("episode_num") val episodeNum: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("container_extension") val containerExtension: String?,
    @SerializedName("info") val info: SeriesEpisodeInfoDto?,
    @SerializedName("movie_image") val movieImage: String?
)

data class SeriesEpisodeInfoDto(
    @SerializedName("plot") val plot: String?,
    @SerializedName("duration") val duration: String?,
    @SerializedName("releasedate") val releaseDate: String?,
    @SerializedName("movie_image") val movieImage: String?
)
