package com.poc.iptvxtream.data.remote.dto

import com.google.gson.annotations.SerializedName

data class VodStreamDto(
    @SerializedName("stream_id") val streamId: Int?,
    @SerializedName("name") val name: String?,
    @SerializedName("stream_icon") val streamIcon: String?,
    @SerializedName("rating") val rating: String?,
    @SerializedName("added") val added: String?,
    @SerializedName("category_id") val categoryId: String?
)
