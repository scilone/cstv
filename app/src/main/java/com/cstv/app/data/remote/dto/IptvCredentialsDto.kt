package com.cstv.app.data.remote.dto

data class IptvCredentialsRequestDto(val host: String, val port: Int, val username: String, val password: String)

data class IptvCredentialsDto(val host: String, val port: Int, val username: String, val password: String, val updatedAt: String? = null)
