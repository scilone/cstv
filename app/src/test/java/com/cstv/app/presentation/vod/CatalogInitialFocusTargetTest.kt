package com.cstv.app.presentation.vod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogInitialFocusTargetTest {
    @Test
    fun allModeUsesResumeThenFavoritesThenFirstCategory() {
        assertEquals(CatalogFocusTarget.RESUME, CatalogInitialFocusTarget.of(true, true, true, "action", 0))
        assertEquals(CatalogFocusTarget.FAVORITES, CatalogInitialFocusTarget.of(true, false, true, "action", 0))
        assertEquals(CatalogFocusTarget.FIRST_CATEGORY, CatalogInitialFocusTarget.of(true, false, false, "action", 0))
    }

    @Test
    fun gridNeedsAComposedCellAndEmptyAllModeHasNoTarget() {
        assertEquals(CatalogFocusTarget.GRID_FIRST_CELL, CatalogInitialFocusTarget.of(false, false, false, null, 1))
        assertNull(CatalogInitialFocusTarget.of(false, false, false, null, 0))
        assertNull(CatalogInitialFocusTarget.of(true, false, false, null, 0))
    }
}
