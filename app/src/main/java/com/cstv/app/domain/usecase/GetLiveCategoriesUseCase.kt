package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.CategoryType
import com.cstv.app.domain.model.LiveCategory
import com.cstv.app.domain.model.applyCategoryPreferences
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import com.cstv.app.domain.repository.LiveTvRepository
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
