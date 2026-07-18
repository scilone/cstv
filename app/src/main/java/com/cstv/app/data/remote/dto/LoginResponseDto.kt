package com.cstv.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class LoginResponseDto(
    @SerializedName("user_info") val userInfo: UserInfoDto?,
    @SerializedName("server_info") val serverInfo: ServerInfoDto?
)

data class UserInfoDto(
    @SerializedName("username") val username: String?,
    @SerializedName("password") val password: String?,
    @SerializedName("message") val message: String?,
    @SerializedName("auth") val auth: Int?,
    @SerializedName("status") val status: String?,
    @SerializedName("exp_date") val expDate: Long?,
    @SerializedName("is_trial") val isTrial: Int?,
    @SerializedName("active_cons") val activeCons: Int?,
    @SerializedName("max_connections") val maxConnections: Int?
)

data class ServerInfoDto(
    @SerializedName("url") val url: String?,
    @SerializedName("port") val port: Int?
)
