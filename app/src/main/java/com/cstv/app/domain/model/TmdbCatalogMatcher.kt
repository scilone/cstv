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
        val score: Double
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
        val matches = mutableListOf<T>()

        for (candidate in catalog) {
            if (candidate.id in excludedIds || !isYearCompatible(tmdbYear, candidate.releaseYear)) continue

            val score = ApproximateTitleMatcher.computeSimilarityNormalized(
                normalizedTmdbTitle,
                candidate.normalizedTitle
            )
            if (score < MIN_SIMILARITY) continue

            when {
                score > bestScore -> {
                    bestScore = score
                    matches.clear()
                    matches += candidate.item
                }
                abs(score - bestScore) < SCORE_EQUALITY_EPSILON -> matches += candidate.item
            }
        }

        return matches.takeIf { it.isNotEmpty() }?.let { Match(it, bestScore) }
    }

    private fun isYearCompatible(tmdbYear: Int?, iptvYear: Int?): Boolean =
        tmdbYear == null || iptvYear == null || iptvYear <= 0 ||
            abs(tmdbYear.toLong() - iptvYear.toLong()) <= 1L
}
