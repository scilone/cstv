package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.CategoryType
import com.cstv.app.domain.model.VodCategory
import com.cstv.app.domain.model.applyCategoryPreferences
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import com.cstv.app.domain.repository.VodRepository
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
