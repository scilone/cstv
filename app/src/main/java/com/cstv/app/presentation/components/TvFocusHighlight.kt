package com.cstv.app.presentation.components

import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cstv.app.presentation.theme.AccentLavande

/**
 * Marquage de focus commun aux vignettes de média sur TV.
 *
 * Un trait fin doublé d'un halo diffus : un liseré épais opaque durcit la
 * vignette et écrase l'affiche, alors qu'à distance c'est le halo qui porte la
 * lisibilité. Les deux traits partagent les mêmes bornes — le halo, plus large,
 * est dessiné en premier et le trait net vient se poser sur son bord extérieur.
 */
fun Modifier.tvFocusHighlight(
    focused: Boolean,
    shape: Shape,
    strokeWidth: Dp = 1.5.dp,
    haloWidth: Dp = 6.dp,
    color: Color = AccentLavande,
    restingColor: Color = Color.Transparent,
    restingWidth: Dp = 0.dp
): Modifier = if (focused) {
    this
        .border(haloWidth, color.copy(alpha = 0.16f), shape)
        .border(strokeWidth, color.copy(alpha = 0.95f), shape)
} else {
    this.border(restingWidth, restingColor, shape)
}
