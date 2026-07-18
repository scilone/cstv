package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.CategoryType
import com.cstv.app.domain.model.SeriesCategory
import com.cstv.app.domain.model.applyCategoryPreferences
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import com.cstv.app.domain.repository.SeriesRepository
import javax.inject.Inject

class GetSeriesCategoriesUseCase @Inject constructor(
    private val repository: SeriesRepository,
    private val categoryPreferenceRepository: CategoryPreferenceRepository
) {
    suspend operator fun invoke(forceRefresh: Boolean = false): List<SeriesCategory> {
        val categories = repository.getSeriesCategories(forceRefresh)
        val preferences = categoryPreferenceRepository.getPreferences(CategoryType.SERIES)
        return applyCategoryPreferences(categories, preferences) { it.categoryId }
    }
}
