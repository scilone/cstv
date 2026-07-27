package com.cstv.app.domain.model

/**
 * Sélection ordonnée des « titres associés » à un média, à partir de ses
 * genres. Objet pur et testable — aucune dépendance Android/Room.
 *
 * Ordre : d'abord par un rang décroissant, exprimé en dixièmes : chaque genre
 * commun et la catégorie commune valent 1,0 ; chaque genre supplémentaire du
 * candidat retire 0,1, dans la limite de 0,9. À rang égal, on départage par un
 * score combinant la note et la date d'ajout. Formellement :
 * `rankTenths = (shared + catBonus) * 10 - min(9, extras)`. Les candidats sans
 * aucun genre commun sont exclus (la section reste ancrée sur les genres).
 */
object RelatedTitlesSelector {

    data class Candidate<T>(
        val item: T,
        val genres: List<String>,
        val rating: Double,
        val added: Long,
        val categoryId: String? = null
    )

    // Départage (à rang égal) : la note pèse plus que la fraîcheur d'ajout.
    private const val W_RATING = 0.7
    private const val W_ADDED = 0.3
    // Unité du rang : 10 dixièmes représentent un genre partagé ou le bonus catégorie.
    private const val RANK_SCALE = 10
    private const val MALUS_PER_EXTRA_TENTHS = 1
    private const val MALUS_CAP_TENTHS = 9

    fun <T> select(
        currentGenres: List<String>,
        currentCategoryId: String?,
        candidates: List<Candidate<T>>,
        limit: Int
    ): List<T> {
        if (currentGenres.isEmpty() || candidates.isEmpty() || limit <= 0) return emptyList()
        val target = currentGenres
            .map(GenreParser::normalize)
            .filter(String::isNotEmpty)
            .toSet()
        if (target.isEmpty()) return emptyList()

        data class Scored<T>(val item: T, val rankTenths: Int, val rating: Double, val added: Long)

        val matched = candidates.mapNotNull { c ->
            val candidateGenres = c.genres
                .map(GenreParser::normalize)
                .filter(String::isNotEmpty)
                .toSet()
            val shared = candidateGenres.count { it in target }
            if (shared >= 1) {
                // La catégorie commune vaut un genre commun de plus.
                val sameCat = currentCategoryId != null && c.categoryId == currentCategoryId
                val extras = candidateGenres.size - shared
                val malusTenths = (extras * MALUS_PER_EXTRA_TENTHS).coerceAtMost(MALUS_CAP_TENTHS)
                val rankTenths = (shared + if (sameCat) 1 else 0) * RANK_SCALE - malusTenths
                Scored(c.item, rankTenths, c.rating, c.added)
            } else null
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

        data class Ranked<T>(val item: T, val rankTenths: Int, val secondaryScore: Double)

        return matched
            .map { Ranked(it.item, it.rankTenths, score(it)) }
            .sortedWith(
                compareByDescending<Ranked<T>> { it.rankTenths }
                    .thenByDescending { it.secondaryScore }
            )
            .take(limit)
            .map { it.item }
    }
}
