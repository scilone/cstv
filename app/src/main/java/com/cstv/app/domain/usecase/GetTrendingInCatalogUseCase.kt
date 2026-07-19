package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.ApproximateTitleMatcher
import com.cstv.app.domain.model.CategoryType
import com.cstv.app.domain.model.SeriesStream
import com.cstv.app.domain.model.TitleNormalizer
import com.cstv.app.domain.model.TrendingCatalogItem
import com.cstv.app.domain.model.TrendingTitle
import com.cstv.app.domain.model.VodStream
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import com.cstv.app.domain.repository.SeriesRepository
import com.cstv.app.domain.repository.TrendingRepository
import com.cstv.app.domain.repository.VodRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Rapproche les tendances TMDB ([TrendingRepository]) du catalogue IPTV local
 * (cache Room via Vod/SeriesRepository) par matching approximatif de titre.
 *
 * Règles :
 *  - un `TrendingTitle` film ne cherche que dans les films, une série que dans
 *    les séries (respect de `isMovie`) ;
 *  - les catégories masquées du profil actif sont exclues **avant** le matching
 *    (même esprit que [AdvancedCatalogSearchUseCase]) ;
 *  - on garde le meilleur candidat au-dessus du seuil, dédupliqué par flux, et
 *    au plus [MAX_RESULTS] résultats, dans l'ordre TMDB ;
 *  - tout échec (pas de clé, réseau, cache) → liste vide, jamais d'exception
 *    propagée à la présentation.
 *
 * Exécuté sur [Dispatchers.Default] : parcours potentiel de milliers d'items
 * avec Levenshtein par candidat, à tenir hors du thread Main.
 */
class GetTrendingInCatalogUseCase @Inject constructor(
    private val trendingRepository: TrendingRepository,
    private val vodRepository: VodRepository,
    private val seriesRepository: SeriesRepository,
    private val categoryPreferenceRepository: CategoryPreferenceRepository
) {
    suspend operator fun invoke(): List<TrendingCatalogItem> = withContext(Dispatchers.Default) {
        val trending = safeCall { trendingRepository.getTrending() } ?: emptyList()
        if (trending.isEmpty()) return@withContext emptyList()

        val hasMovie = trending.any { it.isMovie }
        val hasTv = trending.any { !it.isMovie }

        // Pré-normalise chaque flux visible une seule fois (name → forme comparable).
        val vodCandidates: List<Pair<VodStream, String>> = if (hasMovie) {
            val hidden = hiddenCategoryIds(CategoryType.VOD)
            (safeCall { vodRepository.getVodStreams("all", forceRefresh = false) } ?: emptyList())
                .asSequence()
                .filter { it.categoryId !in hidden }
                .map { it to TitleNormalizer.normalize(it.name) }
                .filter { it.second.isNotEmpty() }
                .toList()
        } else emptyList()

        val seriesCandidates: List<Pair<SeriesStream, String>> = if (hasTv) {
            val hidden = hiddenCategoryIds(CategoryType.SERIES)
            (safeCall { seriesRepository.getSeriesStreams("all", forceRefresh = false) } ?: emptyList())
                .asSequence()
                .filter { it.categoryId !in hidden }
                .map { it to TitleNormalizer.normalize(it.name) }
                .filter { it.second.isNotEmpty() }
                .toList()
        } else emptyList()

        val seenVod = mutableSetOf<Int>()
        val seenSeries = mutableSetOf<Int>()
        val results = ArrayList<TrendingCatalogItem>(MAX_RESULTS)

        for (t in trending) {
            if (results.size >= MAX_RESULTS) break
            val normTitle = TitleNormalizer.normalize(t.title)
            if (normTitle.isEmpty()) continue

            if (t.isMovie) {
                val best = bestMatch(normTitle, vodCandidates) { it.streamId !in seenVod }
                if (best != null) {
                    seenVod.add(best.streamId)
                    results.add(TrendingCatalogItem(t, vodStream = best, seriesStream = null))
                }
            } else {
                val best = bestMatch(normTitle, seriesCandidates) { it.seriesId !in seenSeries }
                if (best != null) {
                    seenSeries.add(best.seriesId)
                    results.add(TrendingCatalogItem(t, vodStream = null, seriesStream = best))
                }
            }
        }
        results
    }

    /**
     * Meilleur flux dont le titre normalisé dépasse le seuil pour [normTitle],
     * parmi les [candidates] non déjà retenus ([eligible]). `null` si aucun.
     */
    private inline fun <T> bestMatch(
        normTitle: String,
        candidates: List<Pair<T, String>>,
        eligible: (T) -> Boolean
    ): T? {
        var best: T? = null
        var bestScore = ApproximateTitleMatcher.DEFAULT_THRESHOLD
        for ((stream, normName) in candidates) {
            if (!eligible(stream)) continue
            val score = ApproximateTitleMatcher.similarity(normTitle, normName)
            if (score >= bestScore) {
                // >= : à score égal on garde le premier rencontré (ordre catalogue).
                if (best == null || score > bestScore) {
                    best = stream
                    bestScore = score
                }
            }
        }
        return best
    }

    private suspend fun hiddenCategoryIds(type: CategoryType): Set<String> =
        safeCall {
            categoryPreferenceRepository.getPreferences(type)
                .filterValues { it.hidden }
                .keys
        } ?: emptySet()

    private inline fun <R> safeCall(block: () -> R): R? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val MAX_RESULTS = 10
    }
}
