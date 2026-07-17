package com.poc.iptvxtream.domain.usecase

import com.poc.iptvxtream.domain.repository.FavoritesRepository
import javax.inject.Inject

/**
 * Liste des genres distincts (VOD + Séries) disponibles dans le cache local,
 * pour alimenter le filtre par genre de l'écran de recherche. Se remplit au fil
 * de l'enrichissement en arrière-plan des détails d'items.
 */
class GetAvailableGenresUseCase @Inject constructor(
    private val repository: FavoritesRepository
) {
    suspend operator fun invoke(): List<String> = repository.getAvailableGenres()
}
