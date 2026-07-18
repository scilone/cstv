package com.poc.iptvxtream.domain.usecase

import com.poc.iptvxtream.domain.repository.SeriesRepository
import com.poc.iptvxtream.domain.repository.VodRepository
import javax.inject.Inject

// Repli utilisé tant que le catalogue n'a aucun film/série enrichi avec une
// année connue (juste après un premier login, avant la fin du trickle
// d'enrichissement background). Purement une valeur de secours d'affichage,
// jamais utilisée pour filtrer réellement (voir AdvancedSearchFilter).
private const val FALLBACK_MIN_YEAR = 1980
private const val FALLBACK_MAX_YEAR = 2025

/**
 * Bornes (année min, année max) du filtre "année de sortie" de la Recherche
 * avancée, calculées dynamiquement depuis le cache local (films + séries)
 * plutôt que codées en dur, afin de coller au catalogue réel de l'utilisateur.
 */
class GetCatalogYearRangeUseCase @Inject constructor(
    private val vodRepository: VodRepository,
    private val seriesRepository: SeriesRepository
) {
    suspend operator fun invoke(): IntRange {
        val vodBounds = try {
            vodRepository.getReleaseYearBounds()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
        val seriesBounds = try {
            seriesRepository.getReleaseYearBounds()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }

        val mins = listOfNotNull(vodBounds?.first, seriesBounds?.first)
        val maxes = listOfNotNull(vodBounds?.second, seriesBounds?.second)
        if (mins.isEmpty() || maxes.isEmpty()) {
            return FALLBACK_MIN_YEAR..FALLBACK_MAX_YEAR
        }
        return mins.min()..maxes.max()
    }
}
