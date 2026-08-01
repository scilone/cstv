package com.cstv.app.presentation.components

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Test

class TvNavigationRailTest {

    @Test
    fun unselectedDestinationKeepsNormalWeightWhenFocused() {
        assertEquals(FontWeight.Normal, tvRailLabelFontWeight(isSelected = false))
    }

    @Test
    fun selectedDestinationKeepsSelectionWeight() {
        assertEquals(FontWeight.Bold, tvRailLabelFontWeight(isSelected = true))
    }
}
