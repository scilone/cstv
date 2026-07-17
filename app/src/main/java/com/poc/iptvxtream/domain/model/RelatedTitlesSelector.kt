package com.poc.iptvxtream.domain.model

/**
 * Sélection ordonnée des « titres associés » à un média, à partir de ses
 * genres. Objet pur et testable — aucune dépendance Android/Room.
 *
 * Ordre : d'abord par nombre de genres communs décroissant (un candidat qui
 * partage 3 genres passe avant un qui en partage 2, puis 1) ; à nombre de
 * genres communs égal, on départage par un score combinant la note et la date
 * d'ajout du média. Les candidats sans aucun genre commun sont exclus.
 */
object RelatedTitlesSelector {

    data class Candidate<T>(
        val item: T,
        val genres: List<String>,
        val rating: Double,
        val added: Long
    )

    // La note pèse plus que la fraîcheur d'ajout dans le départage.
    private const val W_RATING = 0.7
    private const val W_ADDED = 0.3

    fun <T> select(currentGenres: List<String>, candidates: List<Candidate<T>>, limit: Int): List<T> {
        if (currentGenres.isEmpty() || candidates.isEmpty() || limit <= 0) return emptyList()
        val target = currentGenres.map { it.trim().lowercase() }.toSet()

        data class Scored<T>(val item: T, val shared: Int, val rating: Double, val added: Long)

        val matched = candidates.mapNotNull { c ->
            val shared = c.genres.map { it.trim().lowercase() }.toSet().count { it in target }
            if (shared >= 1) Scored(c.item, shared, c.rating, c.added) else null
        }
        if (matched.isEmpty()) return emptyList()

        // Normalisation min-max de la date d'ajout sur le pool (départage relatif).
        val minAdded = matched.minOf { it.added }
        val maxAdded = matched.maxOf { it.added }
        val addedRange = (maxAdded - minAdded).toDouble()

        fun score(s: Scored<T>): Double {
            val ratingNorm = (s.rating / 10.0).coerceIn(0.0, 1.0)
            val addedNorm = if (addedRange == 0.0) 0.5 else (s.added - minAdded) / addedRange
            return W_RATING * ratingNorm + W_ADDED * addedNorm
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
