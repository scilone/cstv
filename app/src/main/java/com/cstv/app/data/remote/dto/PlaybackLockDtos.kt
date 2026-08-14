package com.cstv.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class PlaybackLockRequestDto(
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("deviceName") val deviceName: String?,
    val takeover: Boolean,
)

data class PlaybackLockDto(
    @SerializedName("lockToken") val lockToken: String,
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("deviceName") val deviceName: String,
    @SerializedName("startedAt") val startedAt: String,
    @SerializedName("heartbeatSeconds") val heartbeatSeconds: Long,
    @SerializedName("ttlSeconds") val ttlSeconds: Long,
)

data class PlaybackLockHolderDto(
    @SerializedName("deviceName") val deviceName: String? = null,
    @SerializedName("heldForSeconds") val heldForSeconds: Long? = null,
)

data class PlaybackLockConflictDto(
    val code: String? = null,
    val holder: PlaybackLockHolderDto? = null,
)
