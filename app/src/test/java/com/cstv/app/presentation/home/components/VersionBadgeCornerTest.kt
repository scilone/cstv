package com.cstv.app.presentation.home.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * F39-R6/R4 : politique « le badge de version n'est jamais masqué, y compris sur une rangée
 * Top 10 » extraite en fonction pure ([versionBadgeCorner]) pour rester testable en JVM sans
 * dépendance Compose (AGENTS.md, aucun device requis).
 */
class VersionBadgeCornerTest {

    @Test
    fun `without a Top 10 rank, the badge stays in the free bottom-left corner`() {
        assertEquals(VersionBadgeCorner.BOTTOM_START, versionBadgeCorner(rank = null))
    }

    @Test
    fun `on a Top 10 row, the badge folds to the top instead of disappearing`() {
        // F39-R6 : avant correction, ces rangées n'affichaient aucun badge — TopRankBadge occupe
        // tout le coin bas-gauche (numéral jusqu'à 158dp de haut), jamais un motif à masquer le
        // badge pour autant.
        assertEquals(VersionBadgeCorner.TOP_CENTER, versionBadgeCorner(rank = 1))
        assertEquals(VersionBadgeCorner.TOP_CENTER, versionBadgeCorner(rank = 10))
    }
}
