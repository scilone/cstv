package com.poc.iptvxtream.domain.usecase

import com.poc.iptvxtream.domain.model.FavoriteItem
import com.poc.iptvxtream.domain.repository.FavoritesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

// Phase 41 : version réactive de GetFavoritesUseCase, pour observer les
// favoris (scopés par profil actif) sans reload manuel après chaque écriture.
class ObserveFavoritesUseCase @Inject constructor(
    private val repository: FavoritesRepository
) {
    operator fun invoke(): Flow<List<FavoriteItem>> = repository.observeFavorites()
}
