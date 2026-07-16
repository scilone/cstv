package com.poc.iptvxtream.domain.usecase

import com.poc.iptvxtream.domain.model.CategoryType
import com.poc.iptvxtream.domain.model.SeriesCategory
import com.poc.iptvxtream.domain.model.applyCategoryPreferences
import com.poc.iptvxtream.domain.repository.CategoryPreferenceRepository
import com.poc.iptvxtream.domain.repository.SeriesRepository
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
