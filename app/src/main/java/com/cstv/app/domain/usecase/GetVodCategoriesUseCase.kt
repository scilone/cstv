package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.CategoryType
import com.cstv.app.domain.model.VodCategory
import com.cstv.app.domain.model.applyCategoryPreferences
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import com.cstv.app.domain.repository.VodRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

/** Voir [GetLiveCategoriesUseCase] : lecture locale observable, jamais réseau. */
class GetVodCategoriesUseCase @Inject constructor(
    private val repository: VodRepository,
    private val categoryPreferenceRepository: CategoryPreferenceRepository
) {
    operator fun invoke(): Flow<List<VodCategory>> = combine(
        repository.observeVodCategories(),
        categoryPreferenceRepository.changes.onStart { emit(Unit) }
    ) { categories, _ ->
        val preferences = categoryPreferenceRepository.getPreferences(CategoryType.VOD)
        applyCategoryPreferences(categories, preferences) { it.categoryId }
    }

    suspend fun once(): List<VodCategory> {
        val categories = repository.getCachedVodCategories()
        val preferences = categoryPreferenceRepository.getPreferences(CategoryType.VOD)
        return applyCategoryPreferences(categories, preferences) { it.categoryId }
    }
}
