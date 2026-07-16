package com.poc.iptvxtream.data.local.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.poc.iptvxtream.domain.model.Credentials
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

    fun clearCredentials() {
        sharedPreferences.edit().clear().apply()
    }
}
