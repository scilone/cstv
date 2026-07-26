package com.cstv.app.domain.model

import kotlin.math.abs

/**
 * Rapproche les titres TMDB et le catalogue local. Les titres IPTV sont
 * normalisés lors de la préparation du catalogue afin d'éviter de répéter ce
 * travail pour chaque tendance.
 */
object TmdbCatalogMatcher {

    private const val MIN_SIMILARITY = 0.8
    private const val SCORE_EQUALITY_EPSILON = 1e-9

    data class CatalogCandidate<T>(
        val item: T,
        val id: Int,
        val normalizedTitle: String,
        val releaseYear: Int?
    )

    data class Match<T>(
        val candidates: List<T>,
        val score: Double,
        val yearRank: Int
    )

    fun prepareMovies(movies: List<VodStream>): List<CatalogCandidate<VodStream>> =
        movies.map { movie ->
            CatalogCandidate(
                item = movie,
                id = movie.streamId,
                normalizedTitle = TitleNormalizer.normalize(movie.name),
                releaseYear = movie.releaseYear?.takeIf { it > 0 }
            )
        }

    fun prepareSeries(series: List<SeriesStream>): List<CatalogCandidate<SeriesStream>> =
        series.map { stream ->
            CatalogCandidate(
                item = stream,
                id = stream.seriesId,
                normalizedTitle = TitleNormalizer.normalize(stream.name),
                releaseYear = stream.releaseYear?.takeIf { it > 0 }
            )
        }

    fun <T> findBestMatches(
        tmdbTitle: String,
        tmdbYear: Int?,
        catalog: List<CatalogCandidate<T>>,
        excludedIds: Set<Int>
    ): Match<T>? {
        val normalizedTmdbTitle = TitleNormalizer.normalize(tmdbTitle)
        var bestScore = 0.0
        // Départage à score textuel égal : un candidat dont l'année est connue
        // et compatible doit toujours l'emporter sur un candidat sans année
        // connue (repli). Sans ce rang, l'ordre du catalogue tranchait seul
        // entre un remake non enrichi et la bonne version datée (bug B14).
        var bestYearRank = Int.MAX_VALUE
        val matches = mutableListOf<T>()

        for (candidate in catalog) {
            if (candidate.id in excludedIds || !isYearCompatible(tmdbYear, candidate.releaseYear)) continue

            val score = ApproximateTitleMatcher.computeSimilarityNormalized(
                normalizedTmdbTitle,
                candidate.normalizedTitle
            )
            if (score < MIN_SIMILARITY) continue

            val rank = yearRankOf(tmdbYear, candidate.releaseYear)

            when {
                score > bestScore -> {
                    bestScore = score
                    bestYearRank = rank
                    matches.clear()
                    matches += candidate.item
                }
                abs(score - bestScore) < SCORE_EQUALITY_EPSILON -> when {
                    rank < bestYearRank -> {
                        bestYearRank = rank
                        matches.clear()
                        matches += candidate.item
                    }
                    rank == bestYearRank -> matches += candidate.item
                    // rank > bestYearRank : candidat moins bien daté à score
                    // égal, écarté au profit du lot déjà retenu.
                }
            }
        }

        return matches.takeIf { it.isNotEmpty() }?.let { Match(it, bestScore, bestYearRank) }
    }

    private fun isYearCompatible(tmdbYear: Int?, iptvYear: Int?): Boolean =
        tmdbYear == null || iptvYear == null || iptvYear <= 0 ||
            abs(tmdbYear.toLong() - iptvYear.toLong()) <= 1L

    /** 0 = année exacte, 1 = tolérance ±1 an, 2 = repli (une année au moins inconnue). */
    private fun yearRankOf(tmdbYear: Int?, iptvYear: Int?): Int {
        if (tmdbYear == null || iptvYear == null || iptvYear <= 0) return 2
        return if (tmdbYear == iptvYear) 0 else 1
    }
}
