package com.cstv.app.presentation.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity

/**
 * Étend l'écran sous la barre d'état, alors même que son conteneur lui réserve
 * cet inset.
 *
 * L'élément est mesuré avec la hauteur de la barre d'état en supplément et
 * remonté d'autant, mais ne déclare que sa hauteur d'origine : il déborde donc
 * vers le haut sans décaler ce qui le suit.
 *
 * Porté par l'écran plutôt que par le conteneur de navigation, à dessein.
 * Moduler le padding du NavHost selon la route provoquait un à-coup à chaque
 * navigation : la destination bascule dès le début de la transition, si bien
 * que l'écran sortant — encore visible — se décalait d'un coup de la hauteur
 * de la barre d'état. Ici, chaque écran garde le même traitement de bout en
 * bout de son animation.
 */
fun Modifier.extendUnderTopInset(): Modifier = composed {
    val extra = with(LocalDensity.current) {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding().roundToPx()
    }
    if (extra <= 0) return@composed this

    layout { measurable, constraints ->
        val placeable = measurable.measure(
            constraints.copy(
                minHeight = if (constraints.minHeight == 0) 0 else constraints.minHeight + extra,
                maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight + extra
                else constraints.maxHeight
            )
        )
        layout(placeable.width, (placeable.height - extra).coerceAtLeast(0)) {
            placeable.place(0, -extra)
        }
    }
}
