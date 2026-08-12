package com.cstv.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class CstvOtpRequestDto(val email: String)
data class CstvOtpVerifyDto(val email: String, val code: String)
data class CstvAccessTokenDto(@SerializedName("accessToken") val accessToken: String, @SerializedName("expiresIn") val expiresIn: Long)
data class CstvProfileDto(val id: String, val name: String, @SerializedName("avatarId") val avatarId: Int, @SerializedName("createdAt") val createdAt: String)
data class CstvAccountDto(val id: String, val email: String, val enabled: Boolean, @SerializedName("activeUntil") val activeUntil: String, val profiles: List<CstvProfileDto>)
data class CstvProfileCreateDto(val name: String, @SerializedName("avatarId") val avatarId: Int)
data class CstvProfileUpdateDto(val name: String? = null, @SerializedName("avatarId") val avatarId: Int? = null)
data class CstvErrorDto(val code: String? = null, val message: String? = null)
data class ObjectMetadataDto(val namespace: String?, val etag: String?, @SerializedName("schemaVersion") val schemaVersion: Int?, @SerializedName("compressedSize") val compressedSize: Int?, @SerializedName("updatedAt") val updatedAt: String?)
data class ObjectListDto(val objects: List<ObjectMetadataDto>)
