package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.SeriesStream
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import com.cstv.app.domain.model.CategoryType
import com.cstv.app.domain.repository.SeriesRepository
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
            if (e is kotlinx.coroutines.CancellationException) throw e
            emptySet<String>()
        }
        return repository.getRelatedSeries(currentSeriesId, genre, limit, hiddenCategories)
    }
}
