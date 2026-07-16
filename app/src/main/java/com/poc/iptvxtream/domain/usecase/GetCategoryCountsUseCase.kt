package com.poc.iptvxtream.domain.usecase

import com.poc.iptvxtream.domain.repository.LiveTvRepository
import com.poc.iptvxtream.domain.repository.SeriesRepository
import com.poc.iptvxtream.domain.repository.VodRepository
import javax.inject.Inject

/**
 * Compteurs de médias par catégorie pour le sélecteur (bottom sheet,
 * iso-maquette). Basés sur le cache Room local : une catégorie jamais
 * synchronisée n'apparaît pas dans la map (le sélecteur n'affiche alors
 * pas de compteur pour elle plutôt qu'un zéro trompeur).
 */
class GetLiveCategoryCountsUseCase @Inject constructor(
    private val repository: LiveTvRepository
) {
    suspend operator fun invoke(): Map<String, Int> = repository.getCategoryCounts()
}

class GetVodCategoryCountsUseCase @Inject constructor(
    private val repository: VodRepository
) {
    suspend operator fun invoke(): Map<String, Int> = repository.getCategoryCounts()
}

class GetSeriesCategoryCountsUseCase @Inject constructor(
    private val repository: SeriesRepository
) {
    suspend operator fun invoke(): Map<String, Int> = repository.getCategoryCounts()
}
