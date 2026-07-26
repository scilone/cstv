package com.cstv.app.presentation.navigation

enum class PlayerDetailsAction {
    POP_TO_DETAILS,
    REPLACE_WITH_DETAILS,
    UNAVAILABLE
}

/**
 * Décide si l'ouverture de fiche depuis un player peut revenir à la fiche déjà
 * présente ou doit remplacer le player par une nouvelle fiche. Cette règle est
 * pure afin de pouvoir être couverte par les tests JVM.
 */
object PlayerDetailsNavigation {
    const val VOD_DETAILS_ROUTE = "vod_details"
    const val SERIES_DETAILS_ROUTE = "series_details"

    fun resolve(
        detailsRoute: String,
        targetId: Int?,
        previousRoute: String?,
        previousTargetId: Int?
    ): PlayerDetailsAction = when {
        targetId == null || targetId <= 0 -> PlayerDetailsAction.UNAVAILABLE
        previousRoute == detailsRoute && previousTargetId == targetId -> {
            PlayerDetailsAction.POP_TO_DETAILS
        }
        else -> PlayerDetailsAction.REPLACE_WITH_DETAILS
    }
}
