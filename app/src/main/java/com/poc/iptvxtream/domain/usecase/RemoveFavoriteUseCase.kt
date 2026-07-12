package com.poc.iptvxtream.domain.usecase

import com.poc.iptvxtream.domain.repository.FavoritesRepository
import javax.inject.Inject

class RemoveFavoriteUseCase @Inject constructor(
    private val repository: FavoritesRepository
) {
    suspend operator fun invoke(id: Int, type: String) {
        repository.removeFavorite(id, type)
    }
}
