package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.CategoryType
import com.cstv.app.domain.model.SeriesCategory
import com.cstv.app.domain.model.applyCategoryPreferences
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import com.cstv.app.domain.repository.SeriesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

/** Voir [GetLiveCategoriesUseCase] : lecture locale observable, jamais réseau. */
class GetSeriesCategoriesUseCase @Inject constructor(
    private val repository: SeriesRepository,
    private val categoryPreferenceRepository: CategoryPreferenceRepository
) {
    operator fun invoke(): Flow<List<SeriesCategory>> = combine(
        repository.observeSeriesCategories(),
        categoryPreferenceRepository.changes.onStart { emit(Unit) }
    ) { categories, _ ->
        val preferences = categoryPreferenceRepository.getPreferences(CategoryType.SERIES)
        applyCategoryPreferences(categories, preferences) { it.categoryId }
    }

    suspend fun once(): List<SeriesCategory> {
        val categories = repository.getCachedSeriesCategories()
        val preferences = categoryPreferenceRepository.getPreferences(CategoryType.SERIES)
        return applyCategoryPreferences(categories, preferences) { it.categoryId }
    }
}
