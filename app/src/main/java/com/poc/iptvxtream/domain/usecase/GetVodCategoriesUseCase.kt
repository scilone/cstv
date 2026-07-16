package com.poc.iptvxtream.domain.usecase

import com.poc.iptvxtream.domain.model.CategoryType
import com.poc.iptvxtream.domain.model.VodCategory
import com.poc.iptvxtream.domain.model.applyCategoryPreferences
import com.poc.iptvxtream.domain.repository.CategoryPreferenceRepository
import com.poc.iptvxtream.domain.repository.VodRepository
import javax.inject.Inject

class GetVodCategoriesUseCase @Inject constructor(
    private val repository: VodRepository,
    private val categoryPreferenceRepository: CategoryPreferenceRepository
) {
    suspend operator fun invoke(forceRefresh: Boolean = false): List<VodCategory> {
        val categories = repository.getVodCategories(forceRefresh)
        val preferences = categoryPreferenceRepository.getPreferences(CategoryType.VOD)
        return applyCategoryPreferences(categories, preferences) { it.categoryId }
    }
}
