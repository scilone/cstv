package com.poc.iptvxtream.domain.model

data class VodStream(
    val streamId: Int,
    val name: String,
    val streamIcon: String?,
    val rating: String?,
    val added: String?,
    val categoryId: String
) {
    /**
     * Build play URL for VOD / Movie:
     * {baseUrl}/movie/{username}/{password}/{stream_id}.{extension}
     */
    fun getPlayUrl(baseUrl: String, username: String, password: String, containerExtension: String = "mp4"): String {
        val cleanBase = baseUrl.trim().removeSuffix("/")
        val cleanExtension = containerExtension.trim().removePrefix(".")
        return "$cleanBase/movie/$username/$password/$streamId.$cleanExtension"
    }
}
