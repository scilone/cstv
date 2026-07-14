package com.poc.iptvxtream.data.remote.dto

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class EpgResponseDto(
    @SerializedName("epg_listings") val epgListings: List<EpgListingDto>?
)

data class EpgListingDto(
    @SerializedName("title") val title: String?,
    @SerializedName("description") val description: String?,
    @SerializedName(value = "start", alternate = ["begin"]) val start: String?,
    @SerializedName(value = "end", alternate = ["stop"]) val end: String?,
    @SerializedName("start_timestamp") val startTimestamp: JsonElement?,
    // Certains panels Xtream nomment ce champ "stop_timestamp" plutôt que
    // "end_timestamp" (asymétrie connue de l'API get_short_epg) : sans
    // l'alias, endTimestamp restait null -> parsé à 0 -> now > 0 toujours
    // vrai -> jauge de progression figée à 100% (voir LiveEpgProgram).
    @SerializedName(value = "end_timestamp", alternate = ["stop_timestamp"]) val endTimestamp: JsonElement?
)