package com.cstv.app.domain.usecase

import com.cstv.app.domain.repository.FavoritesRepository
import javax.inject.Inject

class RemoveFavoriteUseCase @Inject constructor(
    private val repository: FavoritesRepository
) {
    suspend operator fun invoke(id: Int, type: String) {
        repository.removeFavorite(id, type)
    }
}
