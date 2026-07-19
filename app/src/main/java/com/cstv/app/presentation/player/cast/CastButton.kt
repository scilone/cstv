package com.cstv.app.presentation.player.cast

import android.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.cstv.app.R
import com.google.android.gms.cast.framework.CastButtonFactory

/**
 * Bouton Cast standard (icône officielle + dialogue de sélection d'appareil du
 * SDK Cast). À n'afficher que si le Cast est disponible (cf.
 * CastAvailabilityProvider) : sur TV/sans-GMS, ne pas appeler ce composable.
 *
 * `CastButtonFactory.setUpMediaRouteButton` branche le sélecteur de routes et
 * gère l'apparition/disparition selon les appareils Cast détectés sur le réseau.
 */
@Composable
fun CastButton(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            // MediaRouteButton exige un thème Theme.AppCompat : on enveloppe le
            // context (thème app = Material non-AppCompat → crash sinon).
            val themed = ContextThemeWrapper(ctx, R.style.Theme_Cast)
            MediaRouteButton(themed).apply {
                CastButtonFactory.setUpMediaRouteButton(themed, this)
            }
        }
    )
}
