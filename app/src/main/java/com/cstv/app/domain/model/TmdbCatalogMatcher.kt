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
        val yearRank: YearRank
    )

    /** Priorité des candidats à score de titre égal. */
    enum class YearRank {
        EXACT,
        TOLERATED,
        UNKNOWN
    }

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
        var bestYearRank = YearRank.UNKNOWN
        val matches = mutableListOf<Pair<YearRank, T>>()

        for (candidate in catalog) {
            if (candidate.id in excludedIds || !isYearCompatible(tmdbYear, candidate.releaseYear)) continue

            val score = ApproximateTitleMatcher.computeSimilarityNormalized(
                normalizedTmdbTitle,
                candidate.normalizedTitle
            )
            if (score < MIN_SIMILARITY) continue

            val rank = yearRankOf(tmdbYear, candidate.releaseYear)

            when {
                isStrictlyBetterScore(score, bestScore) -> {
                    bestScore = score
                    bestYearRank = rank
                    matches.clear()
                    matches += rank to candidate.item
                }
                abs(score - bestScore) <= SCORE_EQUALITY_EPSILON -> when {
                    rank < bestYearRank -> {
                        bestYearRank = rank
                        // Conserver les replis après le candidat le mieux daté :
                        // les use cases les consultent après filtre de catégorie
                        // masquée ou de suppression en base.
                        matches += rank to candidate.item
                    }
                    rank == bestYearRank -> matches += rank to candidate.item
                    else -> matches += rank to candidate.item
                }
            }
        }

        return matches.takeIf { it.isNotEmpty() }?.let { rankedMatches ->
            Match(
                candidates = rankedMatches.sortedBy { it.first }.map { it.second },
                score = bestScore,
                yearRank = bestYearRank
            )
        }
    }

    private fun isYearCompatible(tmdbYear: Int?, iptvYear: Int?): Boolean =
        !tmdbYear.isKnownYear() || !iptvYear.isKnownYear() ||
            abs(tmdbYear!!.toLong() - iptvYear!!.toLong()) <= 1L

    private fun yearRankOf(tmdbYear: Int?, iptvYear: Int?): YearRank = when {
        !tmdbYear.isKnownYear() || !iptvYear.isKnownYear() -> YearRank.UNKNOWN
        tmdbYear == iptvYear -> YearRank.EXACT
        else -> YearRank.TOLERATED
    }

    private fun Int?.isKnownYear(): Boolean = this != null && this > 0

    internal fun isStrictlyBetterScore(score: Double, bestScore: Double): Boolean =
        score > bestScore + SCORE_EQUALITY_EPSILON
}
