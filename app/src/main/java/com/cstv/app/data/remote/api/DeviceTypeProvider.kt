package com.cstv.app.data.remote.api

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F45 §7.7 : classe l'appareil en `mobile`/`tablet`/`tv` pour l'en-tête `X-CSTV-Device-Type`, qui
 * pilote uniquement le choix de taille d'image côté backend — aucune règle métier locale.
 * Réutilise le même critère TV que [com.cstv.app.MainActivity.isTvDevice] ; un absent/invalide
 * retombe sur `mobile` côté backend, donc la valeur par défaut ici ne casse rien si mal détectée.
 */
@Singleton
class DeviceTypeProvider @Inject constructor(@ApplicationContext private val context: Context) {

    fun current(): String = when {
        isTv() -> "tv"
        isTablet() -> "tablet"
        else -> "mobile"
    }

    private fun isTv(): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    /** Seuil standard Android (`sw600dp`) pour distinguer tablette et téléphone. */
    private fun isTablet(): Boolean =
        context.resources.configuration.smallestScreenWidthDp >= 600
}
