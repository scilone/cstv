package com.cstv.app.data.local.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.model.UserInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredentialsManager @Inject constructor(context: Context) {

    private val sharedPreferences: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "secret_shared_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
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
        val host = sharedPreferences.getString("host", null) ?: return null
        val port = sharedPreferences.getInt("port", 0)
        val username = sharedPreferences.getString("username", null) ?: return null
        val password = sharedPreferences.getString("password", null) ?: return null
        val rememberMe = sharedPreferences.getBoolean("remember_me", false)

        return Credentials(host, port, username, password, rememberMe)
    }

    /**
     * Date de la dernière validation réseau réussie des identifiants.
     *
     * Stockée ici (chiffré) et non dans `SettingsManager` (préférences en
     * clair) : c'est ce marqueur qui autorise une session hors ligne, il ne
     * doit pas être falsifiable depuis un fichier de préférences lisible.
     */
    fun getLastSuccessfulLoginAt(): Long = sharedPreferences.getLong(KEY_LAST_SUCCESSFUL_LOGIN_AT, 0L)

    fun setLastSuccessfulLoginAt(timestamp: Long) {
        sharedPreferences.edit().putLong(KEY_LAST_SUCCESSFUL_LOGIN_AT, timestamp).apply()
    }

    /** Compte exact dont la dernière validation réseau autorise le repli local. */
    fun getLastValidatedAccountKey(): String? =
        sharedPreferences.getString(KEY_LAST_VALIDATED_ACCOUNT_KEY, null)

    fun setLastValidatedAccountKey(accountKey: String) {
        sharedPreferences.edit().putString(KEY_LAST_VALIDATED_ACCOUNT_KEY, accountKey).apply()
    }

    /**
     * Révoque le droit de session hors ligne sans effacer les identifiants ni
     * le catalogue. Utilisé après un refus explicite du panel.
     */
    fun clearOfflineSessionValidation() {
        sharedPreferences.edit()
            .remove(KEY_LAST_SUCCESSFUL_LOGIN_AT)
            .remove(KEY_LAST_VALIDATED_ACCOUNT_KEY)
            .remove(KEY_LAST_USER_NAME)
            .remove(KEY_LAST_USER_STATUS)
            .remove(KEY_LAST_USER_EXPIRY)
            .remove(KEY_LAST_USER_MAX_CONNECTIONS)
            .remove(KEY_LAST_USER_ACTIVE_CONNECTIONS)
            .remove(KEY_LAST_USER_MESSAGE)
            .apply()
    }

    /**
     * Dernier `UserInfo` validé par le panel. Une session hors ligne l'affiche
     * tel quel, marqué comme daté : sans lui, l'écran de compte n'aurait rien à
     * montrer alors que l'information existe.
     */
    fun saveLastUserInfo(userInfo: UserInfo) {
        sharedPreferences.edit().apply {
            putString(KEY_LAST_USER_NAME, userInfo.username)
            putString(KEY_LAST_USER_STATUS, userInfo.status)
            putString(KEY_LAST_USER_EXPIRY, userInfo.expiryDate)
            putInt(KEY_LAST_USER_MAX_CONNECTIONS, userInfo.maxConnections)
            putInt(KEY_LAST_USER_ACTIVE_CONNECTIONS, userInfo.activeConnections)
            putString(KEY_LAST_USER_MESSAGE, userInfo.message)
            apply()
        }
    }

    fun getLastUserInfo(): UserInfo? {
        val username = sharedPreferences.getString(KEY_LAST_USER_NAME, null) ?: return null
        return UserInfo(
            username = username,
            auth = true,
            status = sharedPreferences.getString(KEY_LAST_USER_STATUS, null) ?: "Active",
            expiryDate = sharedPreferences.getString(KEY_LAST_USER_EXPIRY, null) ?: "Inconnu",
            maxConnections = sharedPreferences.getInt(KEY_LAST_USER_MAX_CONNECTIONS, 1),
            activeConnections = sharedPreferences.getInt(KEY_LAST_USER_ACTIVE_CONNECTIONS, 0),
            message = sharedPreferences.getString(KEY_LAST_USER_MESSAGE, null) ?: "",
            isOfflineSession = true
        )
    }

    fun clearCredentials() {
        sharedPreferences.edit().clear().apply()
    }

    companion object {
        private const val KEY_LAST_SUCCESSFUL_LOGIN_AT = "last_successful_login_at"
        private const val KEY_LAST_VALIDATED_ACCOUNT_KEY = "last_validated_account_key"
        private const val KEY_LAST_USER_NAME = "last_user_name"
        private const val KEY_LAST_USER_STATUS = "last_user_status"
        private const val KEY_LAST_USER_EXPIRY = "last_user_expiry"
        private const val KEY_LAST_USER_MAX_CONNECTIONS = "last_user_max_connections"
        private const val KEY_LAST_USER_ACTIVE_CONNECTIONS = "last_user_active_connections"
        private const val KEY_LAST_USER_MESSAGE = "last_user_message"
    }
}
