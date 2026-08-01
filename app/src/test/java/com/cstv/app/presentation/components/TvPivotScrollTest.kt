package com.cstv.app.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Test

class TvPivotScrollTest {

    @Test
    fun horizontalPivotAt15PercentAnchorsItemLeftEdge() {
        assertEquals(-288, pivotScrollOffset(viewportSize = 1920, itemSize = 130, parentFraction = 0.15f, childFraction = 0f))
    }

    @Test
    fun verticalPivotAt50PercentCentersItem() {
        assertEquals(-390, pivotScrollOffset(viewportSize = 1080, itemSize = 300, parentFraction = 0.5f, childFraction = 0.5f))
    }

    @Test
    fun unmeasuredItemFallsBackToParentFractionOnly() {
        val offset = pivotScrollOffset(viewportSize = 1920, itemSize = 0, parentFraction = 0.15f, childFraction = 0f)
        assertEquals(-Math.round(1920 * 0.15f), offset)
    }

    @Test
    fun unmeasuredViewportProducesNoScroll() {
        assertEquals(0, pivotScrollOffset(viewportSize = 0, itemSize = 130, parentFraction = 0.15f, childFraction = 0f))
    }

    @Test
    fun itemLargerThanViewportProducesExactPositiveOffset() {
        // Review F19, Mineur #2 : la version précédente vérifiait qu'un `Int`
        // n'est pas "NaN", ce qu'il ne peut structurellement jamais être — le
        // test ne démontrait rien. Remplacée par une valeur exacte.
        assertEquals(2400, pivotScrollOffset(viewportSize = 200, itemSize = 5000, parentFraction = 0.5f, childFraction = 0.5f))
    }

    @Test
    fun hugeItemSizeNearIntRangeDoesNotOverflow() {
        // itemSize proche de l'échelle d'Int.MAX_VALUE : si le calcul était un
        // jour réécrit en arithmétique Int pure (au lieu de Float), une telle
        // valeur ferait déborder le produit intermédiaire et boucler vers un
        // nombre négatif. `2.0f.pow(30)` reste exactement représentable en
        // Float (puissance de deux), donc le résultat attendu est exact.
        val hugeItemSize = 1_073_741_824 // 2^30
        assertEquals(
            536_870_912, // 2^29
            pivotScrollOffset(viewportSize = 200, itemSize = hugeItemSize, parentFraction = 0f, childFraction = 0.5f)
        )
    }

    @Test
    fun hugeViewportSizeNearIntRangeDoesNotOverflow() {
        val hugeViewportSize = 1_073_741_824 // 2^30
        assertEquals(
            -1_073_741_824,
            pivotScrollOffset(viewportSize = hugeViewportSize, itemSize = 0, parentFraction = 1f, childFraction = 0f)
        )
    }

    @Test
    fun nonIntegerFractionRoundsDeterministically() {
        val offset = pivotScrollOffset(viewportSize = 1001, itemSize = 0, parentFraction = 0.15f, childFraction = 0f)
        assertEquals(-150, offset)
    }
}
