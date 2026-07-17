package com.poc.iptvxtream.domain.usecase

import com.poc.iptvxtream.domain.model.VodStream
import com.poc.iptvxtream.domain.repository.VodRepository
import javax.inject.Inject

/** Films associés (mêmes genres) à afficher en bas des détails d'un film. */
class GetRelatedMoviesUseCase @Inject constructor(
    private val repository: VodRepository
) {
    suspend operator fun invoke(currentStreamId: Int, genre: String?, limit: Int = 10): List<VodStream> =
        repository.getRelatedMovies(currentStreamId, genre, limit)
}
