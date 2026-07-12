package com.poc.iptvxtream.data.remote.dto

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class VodInfoResponseDto(
    @SerializedName("info") val info: VodInfoDto?,
    @SerializedName("movie_data") val movieData: VodMovieDataDto?
)

data class VodInfoDto(
    @SerializedName("name") val name: String?,
    @SerializedName("director") val director: String?,
    @SerializedName("actors") val actors: JsonElement?,
    @SerializedName("cast") val cast: JsonElement?,
    @SerializedName("releasedate") val releaseDate: String?,
    @SerializedName("genre") val genre: String?,
    @SerializedName("plot") val plot: String?,
    @SerializedName("rating") val rating: String?,
    @SerializedName("rating_5") val rating5: String?,
    @SerializedName("cover_big") val coverBig: String?,
    @SerializedName("movie_image") val movieImage: String?,
    @SerializedName("duration") val duration: JsonElement?
)

data class VodMovieDataDto(
    @SerializedName("stream_id") val streamId: Int?,
    @SerializedName("container_extension") val containerExtension: String?
)
