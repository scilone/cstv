package com.cstv.app.presentation.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

class ThemeTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)


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
