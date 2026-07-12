package com.poc.iptvxtream.domain.usecase

import com.poc.iptvxtream.data.local.storage.CategorySorting
import com.poc.iptvxtream.data.local.storage.SettingsManager
import com.poc.iptvxtream.domain.model.VodCategory
import com.poc.iptvxtream.domain.repository.VodRepository
import javax.inject.Inject

class GetVodCategoriesUseCase @Inject constructor(
    private val repository: VodRepository,
    private val settingsManager: SettingsManager
) {
    suspend operator fun invoke(forceRefresh: Boolean = false): List<VodCategory> {
        val categories = repository.getVodCategories(forceRefresh)
        return if (settingsManager.getVodCategorySorting() == CategorySorting.ALPHABETICAL) {
            categories.sortedBy { it.categoryName.lowercase() }
        } else {
            categories
        }
    }
}
