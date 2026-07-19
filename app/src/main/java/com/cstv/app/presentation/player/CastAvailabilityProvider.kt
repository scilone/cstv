package com.cstv.app.presentation.player

import android.content.Context
import android.content.pm.PackageManager
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CastAvailabilityProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Vrai si l'appareil peut caster : ni Android TV/Leanback, ni dépourvu de
     * Google Play Services. Volontairement léger et sans effet de bord —
     * n'initialise PAS le CastContext ici (init lourde, à faire sur le thread
     * principal). Le SDK Cast s'initialise naturellement à l'usage (bouton /
     * session de cast, F4 Tâche 2), où une éventuelle indisponibilité résiduelle
     * se traduit par un simple « aucun appareil trouvé ».
     */
    fun isCastAvailable(): Boolean {
        // Android TV / Leanback : jamais de Cast.
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            return false
        }
        // Google Play Services indispensable au framework Cast.
        return GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }
}
