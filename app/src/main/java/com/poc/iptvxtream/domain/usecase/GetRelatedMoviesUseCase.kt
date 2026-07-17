package com.poc.iptvxtream.domain.usecase

import com.poc.iptvxtream.domain.model.VodStream
import com.poc.iptvxtream.domain.repository.CategoryPreferenceRepository
import com.poc.iptvxtream.domain.model.CategoryType
import com.poc.iptvxtream.domain.repository.VodRepository
import javax.inject.Inject

/** Films associés (mêmes genres) à afficher en bas des détails d'un film. */
class GetRelatedMoviesUseCase @Inject constructor(
    private val repository: VodRepository,
    private val categoryPreferenceRepository: CategoryPreferenceRepository
) {
    suspend operator fun invoke(currentStreamId: Int, genre: String?, limit: Int = 10): List<VodStream> {
        val hiddenCategories = try {
            categoryPreferenceRepository.getPreferences(CategoryType.VOD)
                .filterValues { it.hidden }
                .keys
        } catch (e: Exception) {
            emptySet<String>()
        }
        val allRelated = repository.getRelatedMovies(currentStreamId, genre, limit = 50)
        return allRelated
            .filter { it.categoryId !in hiddenCategories }
            .take(limit)
    }
}
