package com.poc.iptvxtream.domain.usecase

import com.poc.iptvxtream.domain.model.SearchResult
import com.poc.iptvxtream.domain.repository.CategoryPreferenceRepository
import com.poc.iptvxtream.domain.model.CategoryType
import com.poc.iptvxtream.domain.repository.FavoritesRepository
import javax.inject.Inject

class SearchUnifiedUseCase @Inject constructor(
    private val repository: FavoritesRepository,
    private val categoryPreferenceRepository: CategoryPreferenceRepository
) {
    suspend operator fun invoke(query: String): SearchResult {
        val result = repository.searchUnified(query)

        val hiddenLive = getHiddenCategoryIds(CategoryType.LIVE)
        val hiddenVod = getHiddenCategoryIds(CategoryType.VOD)
        val hiddenSeries = getHiddenCategoryIds(CategoryType.SERIES)

        return SearchResult(
            liveResults = result.liveResults.filter { it.categoryId !in hiddenLive },
            vodResults = result.vodResults.filter { it.categoryId !in hiddenVod },
            seriesResults = result.seriesResults.filter { it.categoryId !in hiddenSeries }
        )
    }

    private suspend fun getHiddenCategoryIds(type: CategoryType): Set<String> {
        return try {
            categoryPreferenceRepository.getPreferences(type)
                .filterValues { it.hidden }
                .keys
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            emptySet()
        }
    }
}
