package com.cstv.app.domain.model

data class UserInfo(
    val username: String,
    val auth: Boolean,
    val status: String,
    val expiryDate: String,
    val maxConnections: Int,
    val activeConnections: Int,
    val message: String,
    /**
     * Session accordée sans validation réseau, à partir d'une validation
     * antérieure et d'un catalogue local complet. Les compteurs de connexions
     * et la date d'expiration sont ceux du dernier `UserInfo` connu : ils sont
     * datés, pas garantis à jour.
     */
    val isOfflineSession: Boolean = false
)
