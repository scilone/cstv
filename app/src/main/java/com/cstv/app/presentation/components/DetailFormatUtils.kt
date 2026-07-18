package com.cstv.app.presentation.components

private val YEAR_REGEX = Regex("""\d{4}""")

/**
 * Phase 57 : extrait l'année de sortie d'une date brute pour l'afficher seule
 * sur les fiches détail (ex: "2021-05-14" ou "14 May 2021" -> "2021").
 * Renvoie la valeur d'origine nettoyée si aucune année sur 4 chiffres n'est
 * trouvée.
 */
fun formatReleaseYear(date: String?): String {
    if (date.isNullOrBlank()) return ""
    return YEAR_REGEX.find(date)?.value ?: date.trim()
}
