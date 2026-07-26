package com.cstv.app.domain.model

/** Sélection pure et prudente d'un résultat TMDB pour une fiche Xtream. */
object TrailerLookupMatcher {
    data class Candidate(val id: Int, val title: String?, val year: Int?)

    fun select(title: String?, year: Int?, candidates: List<Candidate>): Candidate? {
        val normalized = TitleNormalizer.normalize(title).takeIf { it.isNotEmpty() } ?: return null
        return candidates.mapIndexedNotNull { index, candidate ->
            val candidateTitle = TitleNormalizer.normalize(candidate.title).takeIf { it.isNotEmpty() } ?: return@mapIndexedNotNull null
            if (year != null && candidate.year != null && kotlin.math.abs(year - candidate.year) > 1) return@mapIndexedNotNull null
            val score = ApproximateTitleMatcher.computeSimilarityNormalized(normalized, candidateTitle)
            candidate.takeIf { score >= 0.8 }?.let { Triple(it, score, index) }
        }.maxWithOrNull(compareBy<Triple<Candidate, Double, Int>> { it.second }.thenBy { -it.third })?.first
    }
}
