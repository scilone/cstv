package com.cstv.app.presentation.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvFocusSelectorStateTest {

    @Test
    fun initialStateHasNoTargetAndIsHidden() {
        val state = TvFocusSelectorState()
        assertNull(state.target)
        assertFalse(state.isVisible)
    }

    @Test
    fun publishStabilisedMakesTheFrameVisibleAndSetsGeometry() {
        val state = TvFocusSelectorState()
        val bounds = Rect(10f, 20f, 130f, 195f)

        state.publishStabilised(bounds, 14.dp)

        assertTrue(state.isVisible)
        assertNotNull(state.target)
        assertEquals(bounds, state.target?.bounds)
        assertEquals(14.dp, state.target?.cornerRadius)
    }

    @Test
    fun twoIdenticalPublicationsLeaveTargetUnchanged() {
        // Propriété centrale du sélecteur statique F23 : deux vignettes
        // successives d'une même rangée qui convergent vers la même
        // géométrie ne doivent produire aucun mouvement perceptible du cadre.
        val state = TvFocusSelectorState()
        val bounds = Rect(10f, 20f, 130f, 195f)

        state.publishStabilised(bounds, 14.dp)
        val firstTarget = state.target

        state.publishStabilised(bounds, 14.dp)
        val secondTarget = state.target

        assertEquals(firstTarget, secondTarget)
    }

    @Test
    fun clearHidesTheFrameWithoutErasingTheLastGeometry() {
        val state = TvFocusSelectorState()
        val bounds = Rect(10f, 20f, 130f, 195f)
        state.publishStabilised(bounds, 14.dp)

        state.clear()

        assertFalse(state.isVisible)
        // La géométrie reste connue : un retour ultérieur du focus n'anime pas
        // depuis un Rect.Zero.
        assertEquals(bounds, state.target?.bounds)
    }

    @Test
    fun publishingAfterClearMakesTheFrameVisibleAgain() {
        val state = TvFocusSelectorState()
        state.publishStabilised(Rect(0f, 0f, 130f, 195f), 14.dp)
        state.clear()

        state.publishStabilised(Rect(50f, 60f, 180f, 255f), 16.dp)

        assertTrue(state.isVisible)
        assertEquals(Rect(50f, 60f, 180f, 255f), state.target?.bounds)
        assertEquals(16.dp, state.target?.cornerRadius)
    }

    // Review F23, Mineur R6 : la conversion de repère (Majeur R2) est extraite
    // en fonction pure `localBounds` précisément pour rester testable en JVM
    // sans dépendance à la mesure Compose réelle.

    @Test
    fun localBounds_hostAtRootOriginLeavesBoundsUnchanged() {
        val rootBounds = Rect(10f, 20f, 130f, 195f)

        val result = localBounds(rootBounds, hostOriginInRoot = Offset.Zero)

        assertEquals(rootBounds, result)
    }

    @Test
    fun localBounds_offsetHostTranslatesBoundsToItsLocalSpace() {
        // Cas concret F23-R2 : le rail TV (largeur w) et l'inset haut (h)
        // décalent la Box d'écran par rapport à la racine de la fenêtre.
        val rootBounds = Rect(210f, 120f, 340f, 315f) // carte mesurée dans la fenêtre
        val hostOriginInRoot = Offset(200f, 100f) // origine de la Box d'écran (rail + inset)

        val result = localBounds(rootBounds, hostOriginInRoot)

        assertEquals(Rect(10f, 20f, 140f, 215f), result)
    }

    @Test
    fun localBounds_preservesWidthAndHeight() {
        val rootBounds = Rect(500f, 300f, 630f, 495f) // 130 x 195
        val hostOriginInRoot = Offset(64f, 48f)

        val result = localBounds(rootBounds, hostOriginInRoot)

        assertEquals(rootBounds.width, result.width, 0f)
        assertEquals(rootBounds.height, result.height, 0f)
    }
}
