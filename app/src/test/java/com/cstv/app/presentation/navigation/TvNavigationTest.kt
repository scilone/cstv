package com.cstv.app.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

class TvNavigationTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)

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
