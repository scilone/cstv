package com.cstv.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class TmdbTrendingResponseDto(
    @SerializedName("results") val results: List<TmdbTrendingItemDto>?
)

data class TmdbTrendingItemDto(
    @SerializedName("id") val id: Any?,
    @SerializedName("title") val title: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("media_type") val mediaType: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("first_air_date") val firstAirDate: String?
)
