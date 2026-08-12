package com.cstv.app.data.local.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.cstv.app.domain.model.CstvSession
import javax.inject.Inject
import javax.inject.Singleton

interface CstvSessionManager {
    fun get(): CstvSession?
    fun save(session: CstvSession)
    fun clearSession()
    fun clearAll()
    fun setLastMeSuccessAt(value: Long)
    fun lastMeSuccessAt(): Long
    fun setAccountActiveUntil(value: Long)
    fun accountActiveUntil(): Long
    fun storedEmail(): String?
}

@Singleton
class CstvSessionManagerImpl @Inject constructor(context: Context) : CstvSessionManager {
    private val preferences: SharedPreferences by lazy {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(context, "cstv_session_prefs", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }

    override fun get(): CstvSession? {
        val token = preferences.getString(KEY_TOKEN, null) ?: return null
        return CstvSession(token, preferences.getString(KEY_ACCOUNT_ID, null),
            preferences.getString(KEY_EMAIL, null), preferences.getLong(KEY_EXPIRES_AT, 0L))
    }

    override fun save(session: CstvSession) {
        preferences.edit().apply {
        putString(KEY_TOKEN, session.token); putString(KEY_ACCOUNT_ID, session.accountId)
        putString(KEY_EMAIL, session.accountEmail); putLong(KEY_EXPIRES_AT, session.expiresAtMillis); apply()
        }
    }

    /** CSTV logout deliberately retains account identity to distinguish a re-login from an account switch. */
    override fun clearSession() = preferences.edit().remove(KEY_TOKEN).remove(KEY_EXPIRES_AT).apply()
    override fun clearAll() = preferences.edit().clear().apply()
    override fun setLastMeSuccessAt(value: Long) = preferences.edit().putLong(KEY_LAST_ME_SUCCESS_AT, value).apply()
    override fun lastMeSuccessAt(): Long = preferences.getLong(KEY_LAST_ME_SUCCESS_AT, 0L)
    override fun setAccountActiveUntil(value: Long) = preferences.edit().putLong(KEY_ACCOUNT_ACTIVE_UNTIL, value).apply()
    override fun accountActiveUntil(): Long = preferences.getLong(KEY_ACCOUNT_ACTIVE_UNTIL, 0L)
    override fun storedEmail(): String? = preferences.getString(KEY_EMAIL, null)

    private companion object { const val KEY_TOKEN = "token"; const val KEY_ACCOUNT_ID = "account_id"; const val KEY_EMAIL = "account_email"; const val KEY_EXPIRES_AT = "token_expires_at"; const val KEY_ACCOUNT_ACTIVE_UNTIL = "account_active_until"; const val KEY_LAST_ME_SUCCESS_AT = "last_me_success_at" }
}
