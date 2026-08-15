package com.cstv.app.domain.model

/**
 * Repli posé par les repositories quand le panel Xtream ne fournit pas un champ
 * texte de la fiche. C'est une sentinelle interne, jamais un genre réel : elle
 * ne doit pas remonter à l'affichage (voir [VodDetails.displayGenre]).
 */
const val UNKNOWN_METADATA = "Inconnu"

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
     * Genre à afficher, ou `null` quand il n'y en a pas à montrer : le champ est
     * vide, ou il porte la sentinelle [UNKNOWN_METADATA]. Une reprise de lecture
     * et une fiche reconstruite depuis le cache court-circuitent toutes deux
     * `get_vod_info`, et affichaient « Inconnu » sous le titre du lecteur.
     */
    val displayGenre: String?
        get() = genre.trim().takeIf { it.isNotEmpty() && !it.equals(UNKNOWN_METADATA, ignoreCase = true) }

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
