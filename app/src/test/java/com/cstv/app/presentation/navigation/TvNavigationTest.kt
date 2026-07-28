package com.cstv.app.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvNavigationTest {
    @Test fun destinations_follow_the_tv_order() = assertEquals(
        listOf("home", "tv", "movies", "series", "search", "settings"),
        TvNavigation.destinations.map { it.route }
    )

    @Test fun only_root_routes_show_the_rail() {
        TvNavigation.destinations.forEach { assertTrue(TvNavigation.isRailRoute(it.route)) }
        listOf(null, "login", "vod_details", "series_details", "live_player", "vod_player", "series_player").forEach {
            assertFalse(TvNavigation.isRailRoute(it))
            assertNull(TvNavigation.railDestinationFor(it))
        }
    }

    @Test fun expiry_label_requires_a_reliable_date() {
        assertEquals("Expire le : 31/12/2026", TvNavigation.expiryLabel("31/12/2026"))
        listOf(null, "", "Illimité", "Inconnu", "31-12-2026").forEach { assertNull(TvNavigation.expiryLabel(it)) }
    }
}
