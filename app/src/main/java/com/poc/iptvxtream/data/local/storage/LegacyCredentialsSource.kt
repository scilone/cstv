package com.poc.iptvxtream.data.local.storage

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.poc.iptvxtream.domain.model.Credentials
import java.io.File

/**
 * Lecture unique des credentials stockés par l'ancien mécanisme
 * (EncryptedSharedPreferences de security-crypto, déprécié) pour la
 * migration transparente vers le chiffrement Keystore maison.
 *
 * TODO(2026-07): retirer cette classe et la dépendance
 * androidx.security:security-crypto une version après la sortie de la
 * release contenant la migration (le temps que les installations
 * existantes soient migrées).
 */
interface LegacyCredentialsSource {
    /**
     * Lit les credentials de l'ancien stockage s'il existe, puis le supprime
     * dans tous les cas (succès, échec de déchiffrement ou absence).
     * @return les credentials migrables, ou null.
     */
    fun readAndPurge(): Credentials?
}

class EncryptedPrefsLegacySource(private val context: Context) : LegacyCredentialsSource {

    override fun readAndPurge(): Credentials? {
        val prefsFile = File(context.filesDir.parentFile, "shared_prefs/$LEGACY_PREFS_NAME.xml")
        if (!prefsFile.exists()) return null

        val credentials = try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                context,
                LEGACY_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            val host = prefs.getString("host", null)
            val username = prefs.getString("username", null)
            val password = prefs.getString("password", null)
            if (host != null && username != null && password != null) {
                Credentials(
                    host = host,
                    port = prefs.getInt("port", 0),
                    username = username,
                    password = password,
                    rememberMe = prefs.getBoolean("remember_me", false)
                )
            } else {
                null
            }
        } catch (e: Exception) {
            // Ancien fichier indéchiffrable (ex : restauration de backup) :
            // on abandonne la migration, l'utilisateur se reconnectera.
            Log.w(TAG, "Migration des anciens credentials impossible", e)
            null
        }

        // deleteSharedPreferences = API 24+ (minSdk 23) : vidage + suppression
        // manuelle du fichier.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            context.deleteSharedPreferences(LEGACY_PREFS_NAME)
        } else {
            context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit()
            prefsFile.delete()
        }
        return credentials
    }

    private companion object {
        const val TAG = "LegacyCredentials"
        const val LEGACY_PREFS_NAME = "secret_shared_prefs"
    }
}
