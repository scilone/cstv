package com.cstv.app.presentation.home.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * F39-R6 : politique « le badge de version n'est jamais masqué » extraite en fonction pure
 * ([versionBadgeCorner]) pour rester testable en JVM sans Compose (AGENTS.md, aucun device requis).
 */
class VersionBadgeCornerTest {

    @Test
    fun `no rank keeps the badge bottom-start`() {
        assertEquals(VersionBadgeCorner.BOTTOM_START, versionBadgeCorner(rank = null))
    }

    @Test
    fun `a top 10 rank folds the badge to top-center`() {
        assertEquals(VersionBadgeCorner.TOP_CENTER, versionBadgeCorner(rank = 1))
        assertEquals(VersionBadgeCorner.TOP_CENTER, versionBadgeCorner(rank = 10))
    }
}
