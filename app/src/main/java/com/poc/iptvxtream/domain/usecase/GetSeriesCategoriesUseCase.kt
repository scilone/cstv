package com.poc.iptvxtream.domain.usecase

import com.poc.iptvxtream.data.local.storage.CategorySorting
import com.poc.iptvxtream.data.local.storage.SettingsManager
import com.poc.iptvxtream.domain.model.SeriesCategory
import com.poc.iptvxtream.domain.repository.SeriesRepository
import javax.inject.Inject

class GetSeriesCategoriesUseCase @Inject constructor(
    private val repository: SeriesRepository,
    private val settingsManager: SettingsManager
) {
    suspend operator fun invoke(forceRefresh: Boolean = false): List<SeriesCategory> {
        val categories = repository.getSeriesCategories(forceRefresh)
        return if (settingsManager.getSeriesCategorySorting() == CategorySorting.ALPHABETICAL) {
            categories.sortedBy { it.categoryName.lowercase() }
        } else {
            categories
        }
    }
}
