package com.cstv.app.domain.model

data class Credentials(
    val host: String,
    val port: Int,
    val username: String,
    val password: String
) {
    override fun toString(): String = "Credentials(host=$host, port=$port, username=$username, password=***)"

    val baseUrl: String
        get() {
            val cleanHost = host.trim().removeSuffix("/")
            val scheme = if (cleanHost.startsWith("http://") || cleanHost.startsWith("https://")) "" else "http://"
            return if (port > 0 && !cleanHost.contains(":$port")) {
                "$scheme$cleanHost:$port"
            } else {
                "$scheme$cleanHost"
            }
        }
}
