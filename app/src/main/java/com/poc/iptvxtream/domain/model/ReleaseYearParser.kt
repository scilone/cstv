package com.poc.iptvxtream.domain.model

/**
 * Parsing défensif de la date de sortie renvoyée par Xtream (`releasedate` /
 * `releaseDate`) en année (Int). Le champ est une chaîne libre remplie par le
 * panel, dans des formats très variables :
 *   "1989", "1989-05-12", "2015-03", "12 May 1989", "05/12/1989",
 *   "Inconnu", "N/A", null, "" ...
 *
 * Objet pur et testable — aucune dépendance Android/Room. Retourne `null` dès
 * qu'aucune année plausible n'est trouvée, plutôt que de lever une exception.
 */
object ReleaseYearParser {

    // Année plausible : un siècle 19xx ou 20xx. Borne haute laissée large
    // (jusqu'à 2099) pour ne pas rejeter des sorties futures annoncées.
    private val YEAR_REGEX = Regex("(?:19|20)\\d{2}")
    private const val MIN_YEAR = 1900
    private const val MAX_YEAR = 2099

    /**
     * Extrait la première année plausible (1900–2099) de [raw].
     * Renvoie `null` si [raw] est nul/vide/placeholder ou ne contient aucune
     * année reconnaissable.
     */
    fun parseYear(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val match = YEAR_REGEX.find(raw) ?: return null
        val year = match.value.toIntOrNull() ?: return null
        return if (year in MIN_YEAR..MAX_YEAR) year else null
    }
}
