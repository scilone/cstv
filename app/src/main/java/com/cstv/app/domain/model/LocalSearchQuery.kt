package com.cstv.app.domain.model

import java.text.Normalizer
import java.util.Locale

/**
 * Sémantique pure de recherche locale, partagée par les recherches unifiée et avancée.
 * Le blob persistant et les tokens utilisateur sont normalisés avant comparaison :
 * le repli de casse ASCII-only de SQLite ne peut donc pas modifier le résultat.
 */
class LocalSearchQuery private constructor(
    private val tokens: List<String>
) {
    val isEmpty: Boolean get() = tokens.isEmpty()
    private val anchor: String get() = tokens.maxByOrNull { it.length }.orEmpty()
    val likePattern: String get() = "%${escapeLike(anchor)}%"

    fun matches(searchText: String): Boolean = !isEmpty && tokens.all { searchText.contains(it) }

    companion object {
        fun parse(raw: String): LocalSearchQuery = LocalSearchQuery(
            raw.trim().split(WHITESPACE).map(::normalize).filter { it.isNotEmpty() }
        )

        fun normalize(value: String): String {
            val lower = value.lowercase(Locale.ROOT)
            val expanded = buildString(lower.length) {
                lower.forEach { char -> append(FOLD_MAP[char] ?: char) }
            }
            return Normalizer.normalize(expanded, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")
        }

        fun escapeLike(token: String): String = token
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

        fun buildLiveSearchText(name: String, categoryId: String): String =
            buildSearchText(name, categoryId)

        fun buildCatalogSearchText(
            name: String,
            actors: String?,
            director: String?,
            genre: String?,
            categoryId: String
        ): String = buildSearchText(name, actors, director, genre, categoryId)

        private fun buildSearchText(vararg fields: String?): String =
            fields.joinToString("\n") { normalize(it.orEmpty()) }

        private val FOLD_MAP = mapOf(
            'œ' to "oe", 'æ' to "ae", 'ß' to "ss", 'ø' to "o", 'ł' to "l", 'đ' to "d"
        )
        private val COMBINING_MARKS = Regex("\\p{Mn}+")
        // Recompiler un Regex coûte cher sur certains chipsets Android TV (voir
        // MediaTitleParser, même correctif) — `parse()` tourne à chaque frappe
        // d'une recherche, donc à chaque caractère tapé.
        private val WHITESPACE = Regex("\\s+")
    }
}
