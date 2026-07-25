package com.cstv.app.presentation.navigation

import androidx.navigation.NavController

object MobileNavigation {
    /**
     * Mobile tabs must always pop to Home, not to findStartDestination(): the
     * graph may start at login, which would otherwise be removed from the stack.
     */
    const val ROOT_ROUTE = "home"

    private val DETAIL_ROUTE_TO_TAB = mapOf(
        "vod_details" to "movies",
        "series_details" to "series"
    )

    fun isTabSelected(currentRoute: String?, tabRoute: String): Boolean =
        currentRoute == tabRoute || DETAIL_ROUTE_TO_TAB[currentRoute] == tabRoute
}

/** Every mobile "return to a tab root" navigation must use this single path. */
fun NavController.navigateToRootTab(route: String) {
    val targetIsAlreadySelected = MobileNavigation.isTabSelected(currentDestination?.route, route)
    navigate(route) {
        // A saved stack for the current tab can contain its detail destination;
        // restoring it would put that detail back in front of the tab root.
        popUpTo(MobileNavigation.ROOT_ROUTE) { saveState = !targetIsAlreadySelected }
        launchSingleTop = true
        restoreState = !targetIsAlreadySelected
    }
}
