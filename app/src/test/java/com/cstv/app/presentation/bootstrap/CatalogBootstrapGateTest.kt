package com.cstv.app.presentation.bootstrap

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogBootstrapGateTest {

    @Test
    fun `bootstrap cannot mask login or an unresolved profile gate`() {
        val blocking = CatalogBootstrapUiState(isResolved = true, blocking = true)

        assertFalse(shouldShowCatalogBootstrap(false, true, blocking))
        assertFalse(shouldShowCatalogBootstrap(true, false, blocking))
        assertTrue(shouldShowCatalogBootstrap(true, true, blocking))
    }

    @Test
    fun `complete catalog does not show bootstrap after login and profile gates`() {
        assertFalse(
            shouldShowCatalogBootstrap(
                hasLoggedInUser = true,
                profileGateResolved = true,
                state = CatalogBootstrapUiState(isResolved = true, blocking = false)
            )
        )
    }
}
