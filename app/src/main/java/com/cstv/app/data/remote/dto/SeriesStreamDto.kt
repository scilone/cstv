package com.cstv.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class SeriesStreamDto(
    @SerializedName("series_id") val seriesId: Int?,
    @SerializedName("name") val name: String?,
    @SerializedName("cover") val cover: String?,
    @SerializedName("rating") val rating: String?,
    @SerializedName("added") val added: String?,
    @SerializedName("category_id") val categoryId: String?
)
