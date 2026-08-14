package com.cstv.app.data.local.storage

import android.app.UiModeManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface DeviceIdentityProvider {
    fun deviceId(): String
    fun deviceName(): String?
}

@Singleton
class DeviceIdentityManager @Inject constructor(@ApplicationContext context: Context) : DeviceIdentityProvider {
    private val preferences: SharedPreferences = context.getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
    private val appContext = context.applicationContext

    override fun deviceId(): String = preferences.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
        preferences.edit().putString(KEY_DEVICE_ID, it).apply()
    }

    override fun deviceName(): String? {
        val model = Build.MODEL?.trim()?.takeIf { it.isNotEmpty() && it.none(Char::isISOControl) } ?: return null
        val formFactor = when ((appContext.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType) {
            Configuration.UI_MODE_TYPE_TELEVISION -> "TV"
            Configuration.UI_MODE_TYPE_DESK, Configuration.UI_MODE_TYPE_CAR -> "Tablette"
            else -> "Mobile"
        }
        return "$model ($formFactor)".take(64)
    }

    private companion object { const val KEY_DEVICE_ID = "playback_lock_device_id" }
}
