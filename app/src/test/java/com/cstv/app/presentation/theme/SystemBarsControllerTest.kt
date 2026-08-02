package com.cstv.app.presentation.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

class SystemBarsControllerTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)

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
