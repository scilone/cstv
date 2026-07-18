package com.cstv.app.domain.usecase

import com.cstv.app.domain.repository.FavoritesRepository
import javax.inject.Inject

class IsFavoriteUseCase @Inject constructor(
    private val repository: FavoritesRepository
) {
    suspend operator fun invoke(id: Int, type: String): Boolean {
        return repository.isFavorite(id, type)
    }
}
