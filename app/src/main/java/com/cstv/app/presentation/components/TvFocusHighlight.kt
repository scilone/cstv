package com.cstv.app.presentation.components

import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cstv.app.presentation.theme.AccentLavande

/**
 * Marquage de focus commun aux vignettes de média sur TV.
 *
 * Un trait fin posé sur le bord de la vignette, prolongé vers l'**extérieur**
 * par deux anneaux de plus en plus transparents. Un liseré épais opaque durcit
 * la vignette et un halo dessiné vers l'intérieur rogne l'affiche : seule la
 * diffusion vers l'extérieur détache la vignette de son fond sans manger son
 * contenu.
 */
fun Modifier.tvFocusHighlight(
    focused: Boolean,
    shape: Shape,
    strokeWidth: Dp = 1.5.dp,
    haloWidth: Dp = 6.dp,
    color: Color = AccentLavande,
    restingColor: Color = Color.Transparent,
    restingWidth: Dp = 0.dp
): Modifier = this
    .drawBehind {
        if (!focused) return@drawBehind
        // Anneau dessiné en `Stroke` centré sur un contour agrandi : agrandir de
        // la largeur du trait et décaler d'une demi-largeur place la bande
        // exactement à l'extérieur des bornes, sans recouvrir la vignette.
        fun ring(width: Dp, alpha: Float, gap: Dp) {
            val w = width.toPx()
            val g = gap.toPx()
            val outline = shape.createOutline(
                Size(size.width + w + 2 * g, size.height + w + 2 * g),
                layoutDirection,
                this
            )
            translate(-(w / 2 + g), -(w / 2 + g)) {
                drawOutline(outline, color = color.copy(alpha = alpha), style = Stroke(w))
            }
        }
        ring(width = haloWidth / 2, alpha = 0.26f, gap = 0.dp)
        ring(width = haloWidth, alpha = 0.10f, gap = haloWidth / 2)
    }
    .border(
        width = if (focused) strokeWidth else restingWidth,
        color = if (focused) color.copy(alpha = 0.95f) else restingColor,
        shape = shape
    )
