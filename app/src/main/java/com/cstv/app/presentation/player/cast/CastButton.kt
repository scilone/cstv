package com.cstv.app.presentation.player.cast

import android.view.ContextThemeWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.cstv.app.R
import com.google.android.gms.cast.framework.CastButtonFactory

/**
 * Bouton Cast standard (icône officielle + dialogue de sélection d'appareil du
 * SDK Cast), stylé comme les autres boutons de la barre du lecteur
 * (PlayerTopButton) : boîte 44dp arrondie, fond sombre translucide, bordure.
 * À n'afficher que si le Cast est disponible (cf. CastAvailabilityProvider).
 */
@Composable
fun CastButton(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(44.dp)
            .background(Color(0x59000000), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0x59FFFFFF), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.size(26.dp),
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
}
