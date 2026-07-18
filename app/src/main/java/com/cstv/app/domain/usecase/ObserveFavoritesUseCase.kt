package com.cstv.app.domain.usecase

import com.cstv.app.domain.model.FavoriteItem
import com.cstv.app.domain.repository.FavoritesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

// Phase 41 : version réactive de GetFavoritesUseCase, pour observer les
// favoris (scopés par profil actif) sans reload manuel après chaque écriture.
class ObserveFavoritesUseCase @Inject constructor(
    private val repository: FavoritesRepository
) {
    operator fun invoke(): Flow<List<FavoriteItem>> = repository.observeFavorites()
}
