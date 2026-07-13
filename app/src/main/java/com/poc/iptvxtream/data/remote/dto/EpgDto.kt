package com.poc.iptvxtream.data.remote.dto

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class EpgResponseDto(
    @SerializedName("epg_listings") val epgListings: List<EpgListingDto>?
)

data class EpgListingDto(
    @SerializedName("title") val title: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("start") val start: String?,
    @SerializedName("end") val end: String?,
    @SerializedName("start_timestamp") val startTimestamp: JsonElement?,
    @SerializedName("end_timestamp") val endTimestamp: JsonElement?
)