package com.cstv.app.presentation.player.cast

import android.content.Context
import android.content.pm.PackageManager
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/**
 * Vrai si l'appareil peut caster : ni Android TV/Leanback, ni dépourvu de Google
 * Play Services. Volontairement léger et sans effet de bord — n'initialise PAS
 * le CastContext (init lourde, thread principal). Le SDK Cast s'initialise à
 * l'usage (bouton / session).
 *
 * Fonction top-level pour être appelable à la fois par le provider Hilt
 * (CastAvailabilityProvider) et directement par les Composables des lecteurs.
 */
fun isCastAvailable(context: Context): Boolean {
    if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
        return false
    }
    return GoogleApiAvailability.getInstance()
        .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
}
