package com.cstv.app.domain.model

data class VodDetails(
    val streamId: Int,
    val name: String,
    val director: String,
    val actors: String,
    val releaseDate: String,
    val genre: String,
    val plot: String,
    val rating: String,
    val coverBig: String?,
    val containerExtension: String,
    val resumePositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val duration: String? = null,
    /**
     * Fiche reconstruite depuis le catalogue local parce que le panel a refusé
     * ses métadonnées : les champs manquants sont des repli, pas des valeurs
     * fournies par le serveur.
     */
    val isMetadataIncomplete: Boolean = false
) {
    /**
     * Build play URL for VOD / Movie:
     * {baseUrl}/movie/{username}/{password}/{stream_id}.{extension}
     */
    fun getPlayUrl(baseUrl: String, username: String, password: String): String {
        val cleanBase = baseUrl.trim().removeSuffix("/")
        val cleanExtension = containerExtension.trim().removePrefix(".")
        return "$cleanBase/movie/$username/$password/$streamId.$cleanExtension"
    }
}
