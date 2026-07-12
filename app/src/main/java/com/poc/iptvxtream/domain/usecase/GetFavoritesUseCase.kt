package com.poc.iptvxtream.domain.usecase

import com.poc.iptvxtream.domain.model.FavoriteItem
import com.poc.iptvxtream.domain.repository.FavoritesRepository
import javax.inject.Inject

class GetFavoritesUseCase @Inject constructor(
    private val repository: FavoritesRepository
) {
    suspend operator fun invoke(): List<FavoriteItem> {
        return repository.getFavorites()
    }
}
