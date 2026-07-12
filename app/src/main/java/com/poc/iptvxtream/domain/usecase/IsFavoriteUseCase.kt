package com.poc.iptvxtream.domain.usecase

import com.poc.iptvxtream.domain.repository.FavoritesRepository
import javax.inject.Inject

class IsFavoriteUseCase @Inject constructor(
    private val repository: FavoritesRepository
) {
    suspend operator fun invoke(id: Int, type: String): Boolean {
        return repository.isFavorite(id, type)
    }
}
