package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.CategoryType
import com.cstv.app.domain.model.CategoryWithCount
import com.cstv.app.domain.model.SearchMediaType
import com.cstv.app.domain.model.applyCategoryPreferences
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import com.cstv.app.domain.repository.SeriesRepository
import com.cstv.app.domain.repository.VodRepository
import javax.inject.Inject

class GetCategoriesForTypeUseCase @Inject constructor(
    private val vodRepository: VodRepository,
    private val seriesRepository: SeriesRepository,
    private val categoryPreferenceRepository: CategoryPreferenceRepository
) {
    suspend operator fun invoke(mediaType: SearchMediaType?): List<CategoryWithCount> {
        if (mediaType == null) return emptyList()

        return when (mediaType) {
            SearchMediaType.FILM -> {
                val rawCategories = try {
                    vodRepository.getVodCategories(forceRefresh = false)
                } catch (e: Exception) {
                    emptyList()
                }
                val preferences = try {
                    categoryPreferenceRepository.getPreferences(CategoryType.VOD)
                } catch (e: Exception) {
                    emptyMap()
                }
                val visibleCategories = applyCategoryPreferences(rawCategories, preferences) { it.categoryId }
                val counts = try {
                    vodRepository.getCategoryCounts()
                } catch (e: Exception) {
                    emptyMap()
                }

                val mapped = visibleCategories.map { cat ->
                    CategoryWithCount(
                        id = cat.categoryId,
                        name = cat.categoryName,
                        count = counts[cat.categoryId] ?: 0
                    )
                }
                val totalSum = mapped.sumOf { it.count }
                val allEntry = CategoryWithCount(
                    id = "all",
                    name = "Toutes les catégories",
                    count = totalSum
                )
                listOf(allEntry) + mapped
            }
            SearchMediaType.SERIE -> {
                val rawCategories = try {
                    seriesRepository.getSeriesCategories(forceRefresh = false)
                } catch (e: Exception) {
                    emptyList()
                }
                val preferences = try {
                    categoryPreferenceRepository.getPreferences(CategoryType.SERIES)
                } catch (e: Exception) {
                    emptyMap()
                }
                val visibleCategories = applyCategoryPreferences(rawCategories, preferences) { it.categoryId }
                val counts = try {
                    seriesRepository.getCategoryCounts()
                } catch (e: Exception) {
                    emptyMap()
                }

                val mapped = visibleCategories.map { cat ->
                    CategoryWithCount(
                        id = cat.categoryId,
                        name = cat.categoryName,
                        count = counts[cat.categoryId] ?: 0
                    )
                }
                val totalSum = mapped.sumOf { it.count }
                val allEntry = CategoryWithCount(
                    id = "all",
                    name = "Toutes les catégories",
                    count = totalSum
                )
                listOf(allEntry) + mapped
            }
        }
    }
}
