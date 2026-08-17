package com.cstv.app.domain.repository

/**
 * F39 §8.3 : préférence de version mémorisée par profil pour une série,
 * indexée par `linkKey` (T21) — pas par média précis, contrairement à
 * [TrackPreferenceRepository] : le choix porte sur toute l'œuvre, réappliqué
 * automatiquement aux épisodes suivants (décision produit étape 1).
 */
interface SeriesVersionPreferenceRepository {
    /** Identifiant de la série préférée pour cette œuvre, ou `null` si aucune n'est mémorisée. */
    suspend fun getPreferredSeriesId(linkKey: String): Int?

    /** Mémorise le choix explicite pour toute la série. */
    suspend fun setPreference(linkKey: String, preferredSeriesId: Int)

    /** Repli paresseux §8.3 : la série/épisode préféré n'existe plus, la préférence est effacée. */
    suspend fun clearPreference(linkKey: String)
}
