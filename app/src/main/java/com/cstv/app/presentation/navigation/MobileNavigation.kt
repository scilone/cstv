package com.cstv.app.presentation.navigation

import androidx.navigation.NavController

object MobileNavigation {
    /**
     * Mobile tabs must always pop to Home, not to findStartDestination(): the
     * graph may start at login, which would otherwise be removed from the stack.
     */
    const val ROOT_ROUTE = "home"
    const val PENDING_VOD_CATEGORY = "pending_vod_category"
    const val PENDING_SERIES_CATEGORY = "pending_series_category"

    private val DETAIL_ROUTE_TO_TAB = mapOf(
        "vod_details" to "movies",
        "series_details" to "series"
    )

    fun isTabSelected(currentRoute: String?, tabRoute: String): Boolean =
        currentRoute == tabRoute || DETAIL_ROUTE_TO_TAB[currentRoute] == tabRoute
}

/** Every mobile "return to a tab root" navigation must use this single path. */
fun NavController.navigateToRootTab(route: String) {
    if (currentDestination?.route == route) return

    // The tab root is usually still on the back stack, below the detail screen we
    // opened from it. Popping back to it reuses that live entry, so its ViewModel
    // and scroll position survive exactly like a system back press; navigating
    // instead would drop the entry and make the screen reload behind a spinner.
    if (popBackStack(route, inclusive = false)) return

    navigate(route) {
        // saveState/restoreState stay off on purpose: popUpTo always targets
        // ROOT_ROUTE, so the saved stack is keyed to home, and restoring it on a
        // home tab click pushes back the very entries just popped (B11).
        popUpTo(MobileNavigation.ROOT_ROUTE)
        launchSingleTop = true
    }
}
