package com.cstv.app.presentation.components

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
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
 *
 * Devenu `@Composable` (F23) : quand la couche avant du sélecteur pivot fixe
 * est active ([LocalTvFocusSelector] non nul) **et que la carte est
 * focalisée**, elle ne peint plus sa propre bordure — la peindre ici en plus
 * du cadre fixe de l'overlay produirait un « saut » visuel de la bordure
 * avant que la liste n'ait glissé sous le cadre statique, exactement le
 * défaut que F23 supprime. Seul l'anneau **focalisé** est remplacé : une
 * carte non focalisée garde sa bordure de repos (`restingColor`/`restingWidth`)
 * qu'elle ait ou non une couche avant au-dessus d'elle (Review F23, Mineur
 * R5) — sans cette distinction, `HomeLiveTvCard` perdait son filet visible en
 * permanence dès qu'un écran fournissait la couche avant, pas seulement au
 * focus. Hors de la couche avant (écrans hors périmètre F23, mobile), le
 * comportement est inchangé dans tous les cas.
 */
@Composable
fun Modifier.tvFocusHighlight(
    focused: Boolean,
    shape: Shape,
    strokeWidth: Dp = 1.5.dp,
    color: Color = AccentLavande,
    restingColor: Color = Color.Transparent,
    restingWidth: Dp = 0.dp
): Modifier {
    if (focused && LocalTvFocusSelector.current != null) return this
    return this.border(
        width = if (focused) strokeWidth else restingWidth,
        color = if (focused) color.copy(alpha = 0.95f) else restingColor,
        shape = shape
    )
}
