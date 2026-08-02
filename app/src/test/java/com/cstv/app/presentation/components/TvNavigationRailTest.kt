package com.cstv.app.presentation.components

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

class TvNavigationRailTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)


    @Test
    fun unselectedDestinationKeepsNormalWeightWhenFocused() {
        assertEquals(FontWeight.Normal, tvRailLabelFontWeight(isSelected = false))
    }

    @Test
    fun selectedDestinationKeepsSelectionWeight() {
        assertEquals(FontWeight.Bold, tvRailLabelFontWeight(isSelected = true))
    }
}
