package com.poc.iptvxtream.domain.usecase

import com.poc.iptvxtream.domain.model.CategoryType
import com.poc.iptvxtream.domain.model.LiveCategory
import com.poc.iptvxtream.domain.model.applyCategoryPreferences
import com.poc.iptvxtream.domain.repository.CategoryPreferenceRepository
import com.poc.iptvxtream.domain.repository.LiveTvRepository
import javax.inject.Inject

class GetLiveCategoriesUseCase @Inject constructor(
    private val repository: LiveTvRepository,
    private val categoryPreferenceRepository: CategoryPreferenceRepository
) {
    suspend operator fun invoke(forceRefresh: Boolean = false): List<LiveCategory> {
        val categories = repository.getLiveCategories(forceRefresh)
        val preferences = categoryPreferenceRepository.getPreferences(CategoryType.LIVE)
        return applyCategoryPreferences(categories, preferences) { it.categoryId }
    }
}
