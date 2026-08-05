package com.cstv.app.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvCategoryPickerTest {

    private val entries = listOf(
        CategorySheetEntry(id = "all", label = "Tout", count = 42),
        CategorySheetEntry(id = "sport", label = "Sport", count = 10),
        CategorySheetEntry(id = "news", label = "News", count = 5)
    )

    @Test
    fun selectedIdPresentInEntriesIsTheFocusTarget() {
        assertEquals("sport", resolveCategoryPickerFocusTarget(entries, selectedId = "sport"))
    }

    @Test
    fun selectedIdAbsentFallsBackToFirstEntry() {
        // F24-R1 : la catégorie sélectionnée a disparu (rafraîchissement pendant l'ouverture).
        assertEquals("all", resolveCategoryPickerFocusTarget(entries, selectedId = "removed"))
    }

    @Test
    fun nullSelectedIdFallsBackToFirstEntry() {
        assertEquals("all", resolveCategoryPickerFocusTarget(entries, selectedId = null))
    }

    @Test
    fun emptyEntriesResolveToNull() {
        assertNull(resolveCategoryPickerFocusTarget(emptyList(), selectedId = "sport"))
    }
}
