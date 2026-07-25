package com.cstv.app.presentation.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileNavigationTest {
    @Test
    fun rootRouteRemainsHome() {
        assertEquals("home", MobileNavigation.ROOT_ROUTE)
    }

    @Test
    fun detailsSelectTheirOwningTabsOnly() {
        assertTrue(MobileNavigation.isTabSelected("vod_details", "movies"))
        assertTrue(MobileNavigation.isTabSelected("series_details", "series"))
        assertFalse(MobileNavigation.isTabSelected("vod_details", "home"))
        assertFalse(MobileNavigation.isTabSelected("unknown", "movies"))
    }

    @Test
    fun tabSelectionHandlesRootAndUnresolvedRoutes() {
        assertTrue(MobileNavigation.isTabSelected("home", "home"))
        assertFalse(MobileNavigation.isTabSelected(null, "home"))
        assertFalse(MobileNavigation.isTabSelected("vod_details", "series"))
    }
}
