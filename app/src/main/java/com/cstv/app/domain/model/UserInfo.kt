package com.cstv.app.domain.model

data class UserInfo(
    val username: String,
    val auth: Boolean,
    val status: String,
    val expiryDate: String,
    val maxConnections: Int,
    val activeConnections: Int,
    val message: String
)
