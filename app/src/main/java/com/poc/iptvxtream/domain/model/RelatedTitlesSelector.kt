package com.poc.iptvxtream.domain.model

/**
 * Sélection ordonnée des « titres associés » à un média, à partir de ses
 * genres. Objet pur et testable — aucune dépendance Android/Room.
 *
 * Ordre : d'abord par nombre de genres communs décroissant (un candidat qui
 * partage 3 genres passe avant un qui en partage 2, puis 1) ; à nombre de
 * genres communs égal, on départage par un score combinant la note, la
 * proximité de catégorie (même categoryId que le média courant) et la date
 * d'ajout. Les candidats sans aucun genre commun sont exclus.
 */
object RelatedTitlesSelector {

    data class Candidate<T>(
        val item: T,
        val genres: List<String>,
        val rating: Double,
        val added: Long,
        val categoryId: String? = null
    )

    // Départage (à nombre de genres communs égal) : note > catégorie > fraîcheur.
    private const val W_RATING = 0.5
    private const val W_CATEGORY = 0.3
    private const val W_ADDED = 0.2

    fun <T> select(
        currentGenres: List<String>,
        currentCategoryId: String?,
        candidates: List<Candidate<T>>,
        limit: Int
    ): List<T> {
        if (currentGenres.isEmpty() || candidates.isEmpty() || limit <= 0) return emptyList()
        val target = currentGenres.map { it.trim().lowercase() }.toSet()

        data class Scored<T>(val item: T, val shared: Int, val rating: Double, val added: Long, val sameCategory: Boolean)

        val matched = candidates.mapNotNull { c ->
            val shared = c.genres.map { it.trim().lowercase() }.toSet().count { it in target }
            if (shared >= 1) {
                val sameCat = currentCategoryId != null && c.categoryId == currentCategoryId
                Scored(c.item, shared, c.rating, c.added, sameCat)
            } else null
        }
        if (matched.isEmpty()) return emptyList()

        // Normalisation min-max de la date d'ajout sur le pool (départage relatif).
        val minAdded = matched.minOf { it.added }
        val maxAdded = matched.maxOf { it.added }
        val addedRange = (maxAdded - minAdded).toDouble()

        fun score(s: Scored<T>): Double {
            val ratingNorm = (s.rating / 10.0).coerceIn(0.0, 1.0)
            val categoryNorm = if (s.sameCategory) 1.0 else 0.0
            val addedNorm = if (addedRange == 0.0) 0.5 else (s.added - minAdded) / addedRange
            return W_RATING * ratingNorm + W_CATEGORY * categoryNorm + W_ADDED * addedNorm
        }

        return matched
            .sortedWith(
                compareByDescending<Scored<T>> { it.shared }
                    .thenByDescending { score(it) }
            )
            .take(limit)
            .map { it.item }
    }
}
