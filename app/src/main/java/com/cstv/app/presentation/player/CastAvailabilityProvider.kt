package com.cstv.app.presentation.player

import android.content.Context
import com.google.android.gms.cast.framework.CastContext
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
     * Détermine si le SDK Google Cast est disponible et initialisable sur cet appareil.
     * Renvoie false sur Android TV ou appareil sans GMS pour éviter tout crash.
     */
    fun isCastAvailable(): Boolean {
        // Exclure d'emblée Android TV / Leanback
        if (context.packageManager.hasSystemFeature("android.software.leanback")) {
            return false
        }

        return try {
            // 1. Vérifier la présence de Google Play Services
            val availability = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
            if (availability != ConnectionResult.SUCCESS) {
                return false
            }

            // 2. Tenter d'accéder défensivement au CastContext
            CastContext.getSharedInstance(context)
            true
        } catch (e: Exception) {
            false
        }
    }
}
