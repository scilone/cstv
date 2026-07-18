package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.FavoriteItem
import com.cstv.app.domain.repository.FavoritesRepository
import javax.inject.Inject

class AddFavoriteUseCase @Inject constructor(
    private val repository: FavoritesRepository
) {
    suspend operator fun invoke(item: FavoriteItem) {
        repository.addFavorite(item)
    }
}
