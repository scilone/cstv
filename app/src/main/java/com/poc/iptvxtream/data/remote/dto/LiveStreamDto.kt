package com.poc.iptvxtream.data.remote.dto

import com.google.gson.annotations.SerializedName

data class LiveStreamDto(
    @SerializedName("stream_id") val streamId: Int?,
    @SerializedName("name") val name: String?,
    @SerializedName("stream_icon") val streamIcon: String?,
    @SerializedName("epg_channel_id") val epgChannelId: String?,
    @SerializedName("num") val num: Int?,
    @SerializedName("added") val added: String?
)
