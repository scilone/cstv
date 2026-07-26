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
        UNKNOWN
    }

    fun prepareMovies(movies: List<VodStream>): List<CatalogCandidate<VodStream>> =
        movies.map { movie ->
            CatalogCandidate(
                item = movie,
                id = movie.streamId,
                normalizedTitle = TitleNormalizer.normalize(movie.name),
                releaseYear = movie.releaseYear?.takeIf { it > 0 } ?: yearFromTitle(movie.name)
            )
        }

    fun prepareSeries(series: List<SeriesStream>): List<CatalogCandidate<SeriesStream>> =
        series.map { stream ->
            CatalogCandidate(
                item = stream,
                id = stream.seriesId,
                normalizedTitle = TitleNormalizer.normalize(stream.name),
                releaseYear = stream.releaseYear?.takeIf { it > 0 } ?: yearFromTitle(stream.name)
            )
        }

    /**
     * Année lue dans le titre IPTV lui-même (ex. « Odyssée (2016) 1080p »)
     * quand l'enrichissement Xtream n'a pas encore rempli `releaseYear`.
     * Sans ça, l'appariement strict à l'année exacte rejetterait tout le
     * catalogue non encore enrichi.
     *
     * Retourne `null` lorsque l'année EST le titre (« 1917 », « 2012 ») :
     * ce n'est alors pas une date de sortie.
     */
    internal fun yearFromTitle(rawTitle: String?): Int? {
        val year = ReleaseYearParser.parseYear(rawTitle) ?: return null
        if (TitleNormalizer.normalize(rawTitle) == year.toString()) return null
        return year
    }

    fun <T> findBestMatches(
        tmdbTitle: String,
        tmdbYear: Int?,
        catalog: List<CatalogCandidate<T>>,
        excludedIds: Set<Int>
    ): Match<T>? {
        val normalizedTmdbTitle = TitleNormalizer.normalize(tmdbTitle)
        var bestScore = 0.0
        // Tous les candidats retenus partagent l'année TMDB (bug B15) : à score
        // textuel égal, seul l'ordre du catalogue les départage — les versions
        // suivantes servent de repli aux use cases quand la première est
        // masquée par le profil ou supprimée de la base.
        val matches = mutableListOf<T>()

        for (candidate in catalog) {
            if (candidate.id in excludedIds || !isYearCompatible(tmdbYear, candidate.releaseYear)) continue

            val score = ApproximateTitleMatcher.computeSimilarityNormalized(
                normalizedTmdbTitle,
                candidate.normalizedTitle
            )
            if (score < MIN_SIMILARITY) continue

            when {
                isStrictlyBetterScore(score, bestScore) -> {
                    bestScore = score
                    matches.clear()
                    matches += candidate.item
                }
                abs(score - bestScore) <= SCORE_EQUALITY_EPSILON -> matches += candidate.item
            }
        }

        return matches.takeIf { it.isNotEmpty() }?.let { candidates ->
            Match(
                candidates = candidates.toList(),
                score = bestScore,
                yearRank = yearRankOf(tmdbYear)
            )
        }
    }

    // Année exacte obligatoire dès que TMDB en connaît une (bug B15) : la
    // tolérance ±1 et le repli « année locale inconnue » faisaient afficher un
    // homonyme d'une autre décennie (« Odyssée 2026 » côté tendance ouvrant la
    // fiche « Odyssée 2016 »). Un titre non daté vaut mieux qu'un faux titre.
    private fun isYearCompatible(tmdbYear: Int?, iptvYear: Int?): Boolean = when {
        !tmdbYear.isKnownYear() -> true
        !iptvYear.isKnownYear() -> false
        else -> tmdbYear == iptvYear
    }

    /**
     * Un match n'existe que si l'année TMDB est inconnue (aucun filtre possible)
     * ou si le candidat porte exactement la même année.
     */
    private fun yearRankOf(tmdbYear: Int?): YearRank =
        if (tmdbYear.isKnownYear()) YearRank.EXACT else YearRank.UNKNOWN

    private fun Int?.isKnownYear(): Boolean = this != null && this > 0

    internal fun isStrictlyBetterScore(score: Double, bestScore: Double): Boolean =
        score > bestScore + SCORE_EQUALITY_EPSILON
}
