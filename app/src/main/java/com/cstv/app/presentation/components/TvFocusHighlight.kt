package com.cstv.app.presentation.components

import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cstv.app.presentation.theme.AccentLavande

/**
 * Marquage de focus commun aux vignettes de média sur TV : un trait fin posé
 * sur le bord de la vignette.
 *
 * Le halo diffus qui le prolongeait vers l'extérieur a été retiré — deux
 * anneaux dégressifs autour de chaque vignette alourdissaient la grille sans
 * rendre le focus plus lisible que la bordure seule.
 */
fun Modifier.tvFocusHighlight(
    focused: Boolean,
    shape: Shape,
    strokeWidth: Dp = 1.5.dp,
    color: Color = AccentLavande,
    restingColor: Color = Color.Transparent,
    restingWidth: Dp = 0.dp
): Modifier = this.border(
    width = if (focused) strokeWidth else restingWidth,
    color = if (focused) color.copy(alpha = 0.95f) else restingColor,
    shape = shape
)
