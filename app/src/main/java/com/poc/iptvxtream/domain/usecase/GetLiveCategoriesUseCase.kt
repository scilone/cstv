package com.poc.iptvxtream.domain.usecase

import com.poc.iptvxtream.data.local.storage.CategorySorting
import com.poc.iptvxtream.data.local.storage.SettingsManager
import com.poc.iptvxtream.domain.model.LiveCategory
import com.poc.iptvxtream.domain.repository.LiveTvRepository
import javax.inject.Inject

class GetLiveCategoriesUseCase @Inject constructor(
    private val repository: LiveTvRepository,
    private val settingsManager: SettingsManager
) {
    suspend operator fun invoke(forceRefresh: Boolean = false): List<LiveCategory> {
        val categories = repository.getLiveCategories(forceRefresh)
        return if (settingsManager.getTvCategorySorting() == CategorySorting.ALPHABETICAL) {
            categories.sortedBy { it.categoryName.lowercase() }
        } else {
            categories
        }
    }
}
