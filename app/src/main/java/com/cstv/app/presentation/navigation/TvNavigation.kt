package com.cstv.app.presentation.navigation

data class TvRailDestination(
    val route: String,
    val labelRes: Int,
    val icon: TvRailIcon
)

enum class TvRailIcon { HOME, LIVE_TV, MOVIES, SERIES, SEARCH, SETTINGS }

object TvNavigation {
    val destinations = listOf(
        TvRailDestination("home", com.cstv.app.R.string.tv_nav_home, TvRailIcon.HOME),
        TvRailDestination("tv", com.cstv.app.R.string.tv_nav_live, TvRailIcon.LIVE_TV),
        TvRailDestination("movies", com.cstv.app.R.string.tv_nav_movies, TvRailIcon.MOVIES),
        TvRailDestination("series", com.cstv.app.R.string.tv_nav_series, TvRailIcon.SERIES),
        TvRailDestination("search", com.cstv.app.R.string.tv_nav_search, TvRailIcon.SEARCH),
        TvRailDestination("settings", com.cstv.app.R.string.tv_nav_settings, TvRailIcon.SETTINGS)
    )

    fun isRailRoute(route: String?): Boolean = destinations.any { it.route == route }

    fun railDestinationFor(route: String?): TvRailDestination? =
        destinations.firstOrNull { it.route == route }

    fun expiryLabel(expiryDate: String?): String? =
        expiryDate?.takeIf { Regex("\\d{2}/\\d{2}/\\d{4}").matches(it) }
            ?.let { "Expire le : $it" }
}
