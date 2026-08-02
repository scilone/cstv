package com.cstv.app.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

class PlayerDetailsNavigationTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)

    @Test
    fun invalidTargetIsUnavailable() {
        listOf<Int?>(null, 0, -1).forEach { targetId ->
            assertEquals(
                PlayerDetailsAction.UNAVAILABLE,
                PlayerDetailsNavigation.resolve(
                    detailsRoute = PlayerDetailsNavigation.VOD_DETAILS_ROUTE,
                    targetId = targetId,
                    previousRoute = PlayerDetailsNavigation.VOD_DETAILS_ROUTE,
                    previousTargetId = 1
                )
            )
        }
    }

    @Test
    fun sameDetailsImmediatelyBehindPlayerArePoppedTo() {
        assertEquals(
            PlayerDetailsAction.POP_TO_DETAILS,
            PlayerDetailsNavigation.resolve(
                detailsRoute = PlayerDetailsNavigation.VOD_DETAILS_ROUTE,
                targetId = 42,
                previousRoute = PlayerDetailsNavigation.VOD_DETAILS_ROUTE,
                previousTargetId = 42
            )
        )
        assertEquals(
            PlayerDetailsAction.POP_TO_DETAILS,
            PlayerDetailsNavigation.resolve(
                detailsRoute = PlayerDetailsNavigation.SERIES_DETAILS_ROUTE,
                targetId = 42,
                previousRoute = PlayerDetailsNavigation.SERIES_DETAILS_ROUTE,
                previousTargetId = 42
            )
        )
    }

    @Test
    fun differentPreviousEntryRequiresDetailsReplacement() {
        assertEquals(
            PlayerDetailsAction.REPLACE_WITH_DETAILS,
            PlayerDetailsNavigation.resolve(
                detailsRoute = PlayerDetailsNavigation.SERIES_DETAILS_ROUTE,
                targetId = 42,
                previousRoute = PlayerDetailsNavigation.SERIES_DETAILS_ROUTE,
                previousTargetId = 43
            )
        )
        assertEquals(
            PlayerDetailsAction.REPLACE_WITH_DETAILS,
            PlayerDetailsNavigation.resolve(
                detailsRoute = PlayerDetailsNavigation.VOD_DETAILS_ROUTE,
                targetId = 42,
                previousRoute = PlayerDetailsNavigation.VOD_DETAILS_ROUTE,
                previousTargetId = null
            )
        )
        assertEquals(
            PlayerDetailsAction.REPLACE_WITH_DETAILS,
            PlayerDetailsNavigation.resolve(
                detailsRoute = PlayerDetailsNavigation.SERIES_DETAILS_ROUTE,
                targetId = 42,
                previousRoute = null,
                previousTargetId = null
            )
        )
    }

    @Test
    fun filmAndSeriesRoutesAreNotConfused() {
        assertEquals(
            PlayerDetailsAction.REPLACE_WITH_DETAILS,
            PlayerDetailsNavigation.resolve(
                detailsRoute = PlayerDetailsNavigation.SERIES_DETAILS_ROUTE,
                targetId = 42,
                previousRoute = PlayerDetailsNavigation.VOD_DETAILS_ROUTE,
                previousTargetId = 42
            )
        )
    }
}
