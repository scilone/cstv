package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.CategoryType
import com.cstv.app.domain.model.LiveCategory
import com.cstv.app.domain.model.applyCategoryPreferences
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import com.cstv.app.domain.repository.LiveTvRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

/**
 * Catégories Live observées depuis Room, filtrées et ordonnées par les
 * préférences du profil actif. Aucune lecture ne déclenche de réseau : la
 * synchronisation est un chemin séparé.
 */
class GetLiveCategoriesUseCase @Inject constructor(
    private val repository: LiveTvRepository,
    private val categoryPreferenceRepository: CategoryPreferenceRepository
) {
    operator fun invoke(): Flow<List<LiveCategory>> = combine(
        repository.observeLiveCategories(),
        categoryPreferenceRepository.changes.onStart { emit(Unit) }
    ) { categories, _ ->
        val preferences = categoryPreferenceRepository.getPreferences(CategoryType.LIVE)
        applyCategoryPreferences(categories, preferences) { it.categoryId }
    }

    /** Lecture ponctuelle, pour les appelants qui n'observent pas. */
    suspend fun once(): List<LiveCategory> {
        val categories = repository.getCachedLiveCategories()
        val preferences = categoryPreferenceRepository.getPreferences(CategoryType.LIVE)
        return applyCategoryPreferences(categories, preferences) { it.categoryId }
    }
}
