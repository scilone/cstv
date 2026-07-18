package com.cstv.app.presentation.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTest {

    @Test
    fun testColorsAreCorrect() {
        assertEquals(Color(0xFF060608), DarkBackground)
        assertEquals(Color(0xFF9C86FF), AccentLavande)
        assertEquals(Color(0xFF0F0F13), Surface1)
        assertEquals(Color(0xFF16161D), Surface2)
        assertEquals(Color(0xFF1E1E24), Surface3)
    }

    @Test
    fun testTypographyFontMappingIsCorrect() {
        assertEquals(BricolageGrotesque, AppTypography.displayLarge.fontFamily)
        assertEquals(BricolageGrotesque, AppTypography.headlineLarge.fontFamily)
        assertEquals(BricolageGrotesque, AppTypography.titleLarge.fontFamily)
        
        assertEquals(HankenGrotesk, AppTypography.bodyLarge.fontFamily)
        assertEquals(HankenGrotesk, AppTypography.labelLarge.fontFamily)
    }
}
