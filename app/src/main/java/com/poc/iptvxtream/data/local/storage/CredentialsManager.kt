package com.poc.iptvxtream.data.local.storage

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.poc.iptvxtream.domain.model.Credentials
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredentialsManager @Inject constructor(private val context: Context) {

    private val sharedPreferences: SharedPreferences by lazy {
        try {
            createEncryptedPrefs()
        } catch (e: GeneralSecurityException) {
            // Prefs indéchiffrables (ex : restauration de backup sur un autre
            // appareil, la MasterKey Keystore n'existe plus) : on purge le
            // fichier corrompu et on repart à vide, l'utilisateur se reconnecte.
            Log.w(TAG, "Prefs chiffrées corrompues, purge et réinitialisation", e)
            purgePrefsFile()
            createEncryptedPrefs()
        } catch (e: java.io.IOException) {
            Log.w(TAG, "Prefs chiffrées illisibles, purge et réinitialisation", e)
            purgePrefsFile()
            createEncryptedPrefs()
        }
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun purgePrefsFile() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            context.deleteSharedPreferences(PREFS_FILE_NAME)
        } else {
            context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit()
            java.io.File(context.filesDir.parent, "shared_prefs/$PREFS_FILE_NAME.xml").delete()
        }
    }

    fun saveCredentials(credentials: Credentials) {
        sharedPreferences.edit().apply {
            putString("host", credentials.host)
            putInt("port", credentials.port)
            putString("username", credentials.username)
            putString("password", credentials.password)
            putBoolean("remember_me", credentials.rememberMe)
            apply()
        }
    }

    fun getCredentials(): Credentials? {
        return try {
            val host = sharedPreferences.getString("host", null) ?: return null
            val port = sharedPreferences.getInt("port", 0)
            val username = sharedPreferences.getString("username", null) ?: return null
            val password = sharedPreferences.getString("password", null) ?: return null
            val rememberMe = sharedPreferences.getBoolean("remember_me", false)

            Credentials(host, port, username, password, rememberMe)
        } catch (e: SecurityException) {
            // Valeur individuelle indéchiffrable (AEADBadTagException wrappée) :
            // on purge et on force la reconnexion plutôt que de crasher.
            Log.w(TAG, "Déchiffrement des credentials impossible, purge", e)
            purgePrefsFile()
            null
        }
    }

    fun clearCredentials() {
        sharedPreferences.edit().clear().apply()
    }

    companion object {
        private const val TAG = "CredentialsManager"
        private const val PREFS_FILE_NAME = "secret_shared_prefs"
    }
}
