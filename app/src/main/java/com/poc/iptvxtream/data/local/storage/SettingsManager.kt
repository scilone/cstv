package com.poc.iptvxtream.data.local.storage

import android.content.Context
import android.content.SharedPreferences
import com.poc.iptvxtream.domain.model.SubtitleBackground
import com.poc.iptvxtream.domain.model.SubtitleStyle
import com.poc.iptvxtream.domain.model.SubtitleTextColor
import com.poc.iptvxtream.domain.model.SubtitleTextSize
import javax.inject.Inject
import javax.inject.Singleton

enum class SyncFrequency {
    DISABLED,
    DAILY,
    WEEKLY,
    MONTHLY
}

@Singleton
class SettingsManager @Inject constructor(context: Context) {

    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences("app_settings_prefs", Context.MODE_PRIVATE)
    }

    companion object {
        private const val KEY_PREFERRED_AUDIO = "preferred_audio"
        private const val KEY_PREFERRED_SUBTITLE = "preferred_subtitle"
        private const val KEY_SYNC_FREQUENCY = "background_sync_frequency"
        private const val KEY_SUBTITLE_SIZE = "subtitle_text_size"
        private const val KEY_SUBTITLE_COLOR = "subtitle_text_color"
        private const val KEY_SUBTITLE_BACKGROUND = "subtitle_background"
        private const val KEY_LIVE_ALL_SYNCED_AT = "live_all_streams_synced_at"
        private const val KEY_VOD_ALL_SYNCED_AT = "vod_all_streams_synced_at"
        private const val KEY_SERIES_ALL_SYNCED_AT = "series_all_streams_synced_at"
    }

    // --- Horodatage du dernier fetch complet ("Tout") par type de média ---
    // Persisté (au lieu d'une simple variable en mémoire) pour que la
    // fraîcheur du cache "Tout" survive à un redémarrage du process : sans
    // ça, tuer puis relancer l'app faisait recroire à un cache jamais
    // rempli et déclenchait un refetch réseau complet inutile, alors que
    // les données en base étaient encore parfaitement valides.
    fun getLiveAllStreamsSyncedAt(): Long = sharedPreferences.getLong(KEY_LIVE_ALL_SYNCED_AT, 0L)
    fun setLiveAllStreamsSyncedAt(timestamp: Long) {
        sharedPreferences.edit().putLong(KEY_LIVE_ALL_SYNCED_AT, timestamp).apply()
    }

    fun getVodAllStreamsSyncedAt(): Long = sharedPreferences.getLong(KEY_VOD_ALL_SYNCED_AT, 0L)
    fun setVodAllStreamsSyncedAt(timestamp: Long) {
        sharedPreferences.edit().putLong(KEY_VOD_ALL_SYNCED_AT, timestamp).apply()
    }

    fun getSeriesAllStreamsSyncedAt(): Long = sharedPreferences.getLong(KEY_SERIES_ALL_SYNCED_AT, 0L)
    fun setSeriesAllStreamsSyncedAt(timestamp: Long) {
        sharedPreferences.edit().putLong(KEY_SERIES_ALL_SYNCED_AT, timestamp).apply()
    }

    fun getPreferredAudio(): String? {
        return sharedPreferences.getString(KEY_PREFERRED_AUDIO, null)
    }

    fun setPreferredAudio(lang: String?) {
        sharedPreferences.edit().putString(KEY_PREFERRED_AUDIO, lang).apply()
    }

    fun getPreferredSubtitle(): String? {
        return sharedPreferences.getString(KEY_PREFERRED_SUBTITLE, null)
    }

    fun setPreferredSubtitle(lang: String?) {
        sharedPreferences.edit().putString(KEY_PREFERRED_SUBTITLE, lang).apply()
    }

    fun getSyncFrequency(): SyncFrequency {
        val name = sharedPreferences.getString(KEY_SYNC_FREQUENCY, SyncFrequency.DISABLED.name)
        return try {
            SyncFrequency.valueOf(name ?: SyncFrequency.DISABLED.name)
        } catch (e: Exception) {
            SyncFrequency.DISABLED
        }
    }

    fun setSyncFrequency(frequency: SyncFrequency) {
        sharedPreferences.edit().putString(KEY_SYNC_FREQUENCY, frequency.name).apply()
    }

    fun getSubtitleStyle(): SubtitleStyle {
        val size = enumOrDefault(
            sharedPreferences.getString(KEY_SUBTITLE_SIZE, null),
            SubtitleTextSize.MEDIUM
        )
        val color = enumOrDefault(
            sharedPreferences.getString(KEY_SUBTITLE_COLOR, null),
            SubtitleTextColor.WHITE
        )
        val background = enumOrDefault(
            sharedPreferences.getString(KEY_SUBTITLE_BACKGROUND, null),
            SubtitleBackground.NONE
        )
        return SubtitleStyle(size = size, textColor = color, background = background)
    }

    fun setSubtitleStyle(style: SubtitleStyle) {
        sharedPreferences.edit()
            .putString(KEY_SUBTITLE_SIZE, style.size.name)
            .putString(KEY_SUBTITLE_COLOR, style.textColor.name)
            .putString(KEY_SUBTITLE_BACKGROUND, style.background.name)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T {
        return try {
            if (name == null) default else enumValueOf<T>(name)
        } catch (e: IllegalArgumentException) {
            default
        }
    }
}
