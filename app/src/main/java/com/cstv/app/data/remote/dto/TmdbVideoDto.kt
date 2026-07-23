package com.cstv.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class TmdbVideosResponseDto(
    @SerializedName("results") val results: List<TmdbVideoDto>?
)

data class TmdbVideoDto(
    @SerializedName("site") val site: String?,
    @SerializedName("key") val key: String?,
    @SerializedName("type") val type: String?,
    @SerializedName("official") val official: Boolean?
)
