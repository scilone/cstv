package com.cstv.app.data.local.storage

import android.content.Context
import android.content.SharedPreferences
import com.cstv.app.domain.model.SubtitleBackground
import com.cstv.app.domain.model.SubtitleStyle
import com.cstv.app.domain.model.SubtitleTextColor
import com.cstv.app.domain.model.SubtitleTextSize
import javax.inject.Inject
import javax.inject.Singleton

enum class SyncFrequency {
    DISABLED,
    DAILY,
    WEEKLY,
    MONTHLY
}

enum class ResizeMode(val value: Int) {
    FIT(0),  // AspectRatioFrameLayout.RESIZE_MODE_FIT
    FILL(3), // AspectRatioFrameLayout.RESIZE_MODE_FILL
    ZOOM(4)  // AspectRatioFrameLayout.RESIZE_MODE_ZOOM
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
        private const val KEY_RESIZE_MODE = "player_resize_mode"
        private const val KEY_DEBUG_MODE_ENABLED = "debug_mode_enabled"
        private const val KEY_NEW_EPISODES_CHECK_CURSOR = "new_episodes_check_cursor"
        private const val KEY_NEW_EPISODES_SERIES_CURSOR_PREFIX = "new_episodes_series_cursor_"
        private const val KEY_NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"
        private const val KEY_LIVE_QUALITY_MODE_DEFAULT = "live_quality_mode_default"
        private const val KEY_AUTO_PLAY_NEXT_EPISODE_PREFIX = "auto_play_next_episode_"
    }

    // --- F12 : détection de nouveaux épisodes en arrière-plan ---

    /** Index de profil à partir duquel démarrer la prochaine vérification (équité du budget réseau global). */
    fun getNewEpisodesCheckCursor(): Int = sharedPreferences.getInt(KEY_NEW_EPISODES_CHECK_CURSOR, 0)

    fun setNewEpisodesCheckCursor(index: Int) {
        sharedPreferences.edit().putInt(KEY_NEW_EPISODES_CHECK_CURSOR, index).apply()
    }

    /** Index de série à partir duquel démarrer la prochaine vérification pour un profil (équité intra-profil, F12-R1). */
    fun getNewEpisodesSeriesCursor(profileId: Int): Int =
        sharedPreferences.getInt(KEY_NEW_EPISODES_SERIES_CURSOR_PREFIX + profileId, 0)

    fun setNewEpisodesSeriesCursor(profileId: Int, index: Int) {
        sharedPreferences.edit().putInt(KEY_NEW_EPISODES_SERIES_CURSOR_PREFIX + profileId, index).apply()
    }

    /** Une seule demande de `POST_NOTIFICATIONS` sur mobile, jamais répétée après un refus. */
    fun getNotificationPermissionRequested(): Boolean =
        sharedPreferences.getBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, false)

    fun setNotificationPermissionRequested(requested: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, requested).apply()
    }

    // --- Horodatages hérités d'avant T4 (obsolètes) ---
    // La fraîcheur du catalogue vit désormais dans la table Room
    // `catalog_sync_state` : elle est écrite dans la même transaction que les
    // données qu'elle décrit, purgée avec elles et liée au compte — trois
    // propriétés qu'une préférence en clair ne donne pas.
    //
    // Ces trois clés ne sont plus écrites par les repositories. Elles ne
    // subsistent que pour deux usages ponctuels : la reprise unique effectuée
    // par CatalogSyncStateInitializer sur une installation mise à jour, et leur
    // neutralisation lors d'une purge de catalogue. À supprimer une fois le
    // parc migré.
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
        val name = sharedPreferences.getString(KEY_SYNC_FREQUENCY, SyncFrequency.DAILY.name)
        return try {
            SyncFrequency.valueOf(name ?: SyncFrequency.DAILY.name)
        } catch (e: Exception) {
            SyncFrequency.DAILY
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

    fun getResizeMode(): ResizeMode {
        return enumOrDefault(sharedPreferences.getString(KEY_RESIZE_MODE, ResizeMode.FIT.name), ResizeMode.FIT)
    }

    fun setResizeMode(mode: ResizeMode) {
        sharedPreferences.edit().putString(KEY_RESIZE_MODE, mode.name).apply()
    }

    fun getDebugModeEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_DEBUG_MODE_ENABLED, false)
    }

    fun setDebugModeEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_DEBUG_MODE_ENABLED, enabled).apply()
    }

    /** F40: default only for subsequently opened live channels. */
    fun getLiveQualityModeDefault(): Boolean = sharedPreferences.getBoolean(KEY_LIVE_QUALITY_MODE_DEFAULT, false)

    fun setLiveQualityModeDefault(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_LIVE_QUALITY_MODE_DEFAULT, enabled).apply()
    }

    /** Préférence de lecture liée au profil actif, synchronisée par CSTV. */
    fun getAutoPlayNextEpisode(profileId: Int): Boolean =
        sharedPreferences.getBoolean(KEY_AUTO_PLAY_NEXT_EPISODE_PREFIX + profileId, true)

    fun setAutoPlayNextEpisode(profileId: Int, enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_AUTO_PLAY_NEXT_EPISODE_PREFIX + profileId, enabled)
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
