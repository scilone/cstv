package com.cstv.app.domain.repository

import com.cstv.app.domain.model.SeriesWatchState

/**
 * Accès à l'état de suivi des séries pour la détection de nouveaux épisodes
 * (F12). Explicitement paramétré par `profileId` : le worker d'arrière-plan
 * couvre tous les profils, contrairement aux repositories Favoris/Positions
 * qui rattachent leurs flux au seul profil actif.
 */
interface SeriesWatchStateRepository {

    /** Union dédupliquée des séries favorites et de « Continuer à regarder » pour ce profil. */
    suspend fun eligibleSeriesIds(profileId: Int): Set<Int>

    /** États connus pour ce profil, indexés par seriesId. */
    suspend fun getStates(profileId: Int): Map<Int, SeriesWatchState>

    suspend fun upsert(state: SeriesWatchState)
}
