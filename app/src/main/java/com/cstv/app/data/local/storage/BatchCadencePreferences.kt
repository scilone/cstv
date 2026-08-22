package com.cstv.app.data.local.storage

import android.content.Context
import android.content.SharedPreferences
import com.cstv.app.data.worker.BatchCadenceStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * T29 cycle backfill P1 : persistance du dernier départ de requête réseau de matching.
 *
 * `SharedPreferences` dédié plutôt qu'un état statique mutable : le worker peut être relancé dans un
 * process neuf (mort du process entre deux réveils WorkManager) et repartirait alors sans aucune
 * cadence, juste après une rafale. Un unique `Long` local à l'appareil — jamais synchronisé, jamais
 * lié à un profil.
 *
 * Écriture `apply()` : asynchrone, jamais bloquante sur le chemin d'un lot.
 */
@Singleton
class BatchCadencePreferences @Inject constructor(@ApplicationContext context: Context) : BatchCadenceStore {

    private val preferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override suspend fun lastNetworkStartedAtMillis(): Long? =
        preferences.getLong(KEY_LAST_NETWORK_STARTED_AT, ABSENT).takeIf { it != ABSENT }

    override suspend fun setLastNetworkStartedAtMillis(startedAt: Long) {
        preferences.edit().putLong(KEY_LAST_NETWORK_STARTED_AT, startedAt).apply()
    }

    private companion object {
        const val PREFS_NAME = "external_metadata_cadence_prefs"
        const val KEY_LAST_NETWORK_STARTED_AT = "last_network_started_at"
        const val ABSENT = -1L
    }
}
