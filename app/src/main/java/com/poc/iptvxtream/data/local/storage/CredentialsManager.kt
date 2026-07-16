package com.poc.iptvxtream.data.local.storage

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.poc.iptvxtream.domain.model.Credentials
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Stockage sécurisé des credentials Xtream : valeurs chiffrées une à une via
 * [CredentialsCipher] (AES-256-GCM, clé Android Keystore) et stockées en
 * Base64 dans des SharedPreferences standards. Remplace
 * EncryptedSharedPreferences (security-crypto, déprécié par Google) avec
 * migration transparente au premier accès via [LegacyCredentialsSource].
 */
@OptIn(ExperimentalEncodingApi::class)
@Singleton
class CredentialsManager internal constructor(
    private val prefs: SharedPreferences,
    private val cipher: CredentialsCipher,
    private val legacySource: LegacyCredentialsSource?
) {

    @Inject
    constructor(context: Context) : this(
        prefs = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE),
        cipher = KeystoreCredentialsCipher(),
        legacySource = EncryptedPrefsLegacySource(context)
    )

    /** Migration one-shot depuis l'ancien stockage, au premier accès. */
    private val migrated: Unit by lazy {
        if (legacySource != null && !prefs.contains(KEY_HOST)) {
            legacySource.readAndPurge()?.let { saveInternal(it) }
        }
    }

    fun saveCredentials(credentials: Credentials) {
        migrated
        saveInternal(credentials)
    }

    private fun saveInternal(credentials: Credentials) {
        prefs.edit().apply {
            putString(KEY_HOST, encrypt(credentials.host))
            putString(KEY_PORT, encrypt(credentials.port.toString()))
            putString(KEY_USERNAME, encrypt(credentials.username))
            putString(KEY_PASSWORD, encrypt(credentials.password))
            putString(KEY_REMEMBER_ME, encrypt(credentials.rememberMe.toString()))
            apply()
        }
    }

    fun getCredentials(): Credentials? {
        migrated
        return try {
            val host = decrypt(prefs.getString(KEY_HOST, null)) ?: return null
            val username = decrypt(prefs.getString(KEY_USERNAME, null)) ?: return null
            val password = decrypt(prefs.getString(KEY_PASSWORD, null)) ?: return null
            val port = decrypt(prefs.getString(KEY_PORT, null))?.toIntOrNull() ?: 0
            val rememberMe = decrypt(prefs.getString(KEY_REMEMBER_ME, null)).toBoolean()

            Credentials(host, port, username, password, rememberMe)
        } catch (e: Exception) {
            // Déchiffrement impossible (clé Keystore régénérée, valeur
            // corrompue…) : purge et retour null, l'utilisateur se reconnecte.
            Log.w(TAG, "Déchiffrement des credentials impossible, purge", e)
            clearCredentials()
            null
        }
    }

    fun clearCredentials() {
        prefs.edit().clear().apply()
    }

    private fun encrypt(value: String): String =
        Base64.encode(cipher.encrypt(value.encodeToByteArray()))

    private fun decrypt(stored: String?): String? =
        stored?.let { cipher.decrypt(Base64.decode(it)).decodeToString() }

    companion object {
        private const val TAG = "CredentialsManager"
        // Exclu des backups Android (backup_rules.xml/data_extraction_rules.xml) :
        // la clé Keystore n'est pas sauvegardée, les valeurs seraient indéchiffrables.
        private const val PREFS_FILE_NAME = "credentials_prefs"
        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_REMEMBER_ME = "remember_me"
    }
}
