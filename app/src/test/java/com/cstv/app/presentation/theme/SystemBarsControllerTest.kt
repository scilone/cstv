package com.cstv.app.presentation.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemBarsControllerTest {
    @Test
    fun `only video player routes are immersive`() {
        listOf("live_player", "vod_player", "series_player").forEach {
            assertTrue("$it should be immersive", isImmersivePlayerRoute(it))
        }

        listOf(
            null, "login", "profile_selection", "profile_management", "home", "tv", "movies",
            "series", "search", "favorites", "settings", "downloads", "category_management",
            "vod_details", "series_details", "recently_added/false"
        ).forEach {
            assertFalse("$it should not be immersive", isImmersivePlayerRoute(it))
        }
    }
}
