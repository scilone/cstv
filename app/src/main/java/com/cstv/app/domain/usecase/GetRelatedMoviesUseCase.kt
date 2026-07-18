package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.VodStream
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import com.cstv.app.domain.model.CategoryType
import com.cstv.app.domain.repository.VodRepository
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
            if (e is kotlinx.coroutines.CancellationException) throw e
            emptySet<String>()
        }
        return repository.getRelatedMovies(currentStreamId, genre, limit, hiddenCategories)
    }
}
