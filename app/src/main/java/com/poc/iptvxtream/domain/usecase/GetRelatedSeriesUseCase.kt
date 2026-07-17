package com.poc.iptvxtream.domain.usecase

import com.poc.iptvxtream.domain.model.SeriesStream
import com.poc.iptvxtream.domain.repository.CategoryPreferenceRepository
import com.poc.iptvxtream.domain.model.CategoryType
import com.poc.iptvxtream.domain.repository.SeriesRepository
import javax.inject.Inject

/** Séries associées (mêmes genres) à afficher en bas des détails d'une série. */
class GetRelatedSeriesUseCase @Inject constructor(
    private val repository: SeriesRepository,
    private val categoryPreferenceRepository: CategoryPreferenceRepository
) {
    suspend operator fun invoke(currentSeriesId: Int, genre: String?, limit: Int = 10): List<SeriesStream> {
        val hiddenCategories = try {
            categoryPreferenceRepository.getPreferences(CategoryType.SERIES)
                .filterValues { it.hidden }
                .keys
        } catch (e: Exception) {
            emptySet<String>()
        }
        val allRelated = repository.getRelatedSeries(currentSeriesId, genre, limit = 50)
        return allRelated
            .filter { it.categoryId !in hiddenCategories }
            .take(limit)
    }
}
