package com.cstv.app.data.local.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.model.IptvCloudBackupState
import com.cstv.app.domain.model.PendingCloudOp
import javax.inject.Inject
import javax.inject.Singleton

interface IptvCloudBackupStore {
    fun get(): IptvCloudBackupState
    fun save(state: IptvCloudBackupState)
    fun resetForAccount(accountId: String?): IptvCloudBackupState
}

@Singleton
class IptvCloudBackupStoreImpl @Inject constructor(context: Context) : IptvCloudBackupStore {
    private val preferences = EncryptedSharedPreferences.create(context, "iptv_cloud_backup_prefs", MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(), EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    override fun get(): IptvCloudBackupState {
        val accountId = preferences.getString("account_id", null)
        val credentials = listOf("host", "username", "password").map { preferences.getString(it, null) }
        val pendingOp = runCatching {
            PendingCloudOp.valueOf(preferences.getString("pending_op", PendingCloudOp.NONE.name) ?: PendingCloudOp.NONE.name)
        }.getOrDefault(PendingCloudOp.NONE)
        val port = preferences.getInt("port", 0)
        return IptvCloudBackupState(
            accountId = accountId,
            consent = preferences.getBoolean("consent", false),
            lastEtag = preferences.getString("last_etag", null),
            pendingOp = pendingOp,
            credentials = if (credentials.all { it != null } && port in 1..65535) {
                Credentials(credentials[0]!!, port, credentials[1]!!, credentials[2]!!)
            } else {
                null
            }
        )
    }
    override fun save(state: IptvCloudBackupState) {
        preferences.edit().apply {
            putString("account_id", state.accountId)
            putBoolean("consent", state.consent)
            putString("last_etag", state.lastEtag)
            putString("pending_op", state.pendingOp.name)
            state.credentials?.let {
                putString("host", it.host)
                putInt("port", it.port)
                putString("username", it.username)
                putString("password", it.password)
            } ?: run {
                remove("host")
                remove("port")
                remove("username")
                remove("password")
            }
            // A pending revocation must survive a process death immediately after logout.
            commit()
        }
    }
    override fun resetForAccount(accountId: String?): IptvCloudBackupState { val current = get(); return if (current.accountId == accountId) current else IptvCloudBackupState(accountId = accountId).also(::save) }
}
