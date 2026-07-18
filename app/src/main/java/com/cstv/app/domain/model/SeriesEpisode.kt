package com.cstv.app.domain.model

data class SeriesEpisode(
    val id: Int,
    val episodeNum: Int,
    val title: String,
    val containerExtension: String,
    val plot: String,
    val duration: String,
    val releaseDate: String,
    val resumePositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val movieImage: String? = null,
    val lastAccessedAt: Long = 0L,
    val seasonNum: Int = 1
) {
    /**
     * Build play URL for Series Episode:
     * {baseUrl}/series/{username}/{password}/{id}.{extension}
     */
    fun getPlayUrl(baseUrl: String, username: String, password: String): String {
        val cleanBase = baseUrl.trim().removeSuffix("/")
        val cleanExtension = containerExtension.trim().removePrefix(".")
        return "$cleanBase/series/$username/$password/$id.$cleanExtension"
    }
}
