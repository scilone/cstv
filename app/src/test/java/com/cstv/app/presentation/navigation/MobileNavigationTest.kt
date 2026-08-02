package com.cstv.app.presentation.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

class MobileNavigationTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)

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
