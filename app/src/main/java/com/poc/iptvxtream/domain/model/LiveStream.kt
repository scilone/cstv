package com.poc.iptvxtream.domain.model

data class LiveStream(
    val streamId: Int,
    val name: String,
    val streamIcon: String?,
    val epgChannelId: String?,
    val num: Int,
    val categoryId: String
) {
    /**
     * Build play URL for Live Stream:
     * {baseUrl}/live/{username}/{password}/{stream_id}.{extension}
     */
    fun getPlayUrl(baseUrl: String, username: String, password: String, extension: String = "m3u8"): String {
        val cleanBase = baseUrl.trim().removeSuffix("/")
        val cleanExtension = extension.trim().removePrefix(".")
        return "$cleanBase/live/$username/$password/$streamId.$cleanExtension"
    }
}
