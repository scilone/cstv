package com.poc.iptvxtream.data.local.storage

import android.content.Context
import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

enum class CategorySorting {
    DEFAULT, // API order
    ALPHABETICAL
}

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
        private const val KEY_TV_SORTING = "tv_category_sorting"
        private const val KEY_VOD_SORTING = "vod_category_sorting"
        private const val KEY_SERIES_SORTING = "series_category_sorting"
        private const val KEY_PREFERRED_AUDIO = "preferred_audio"
        private const val KEY_PREFERRED_SUBTITLE = "preferred_subtitle"
        private const val KEY_SYNC_FREQUENCY = "background_sync_frequency"
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

    fun getTvCategorySorting(): CategorySorting {
        val name = sharedPreferences.getString(KEY_TV_SORTING, CategorySorting.DEFAULT.name)
        return try {
            CategorySorting.valueOf(name ?: CategorySorting.DEFAULT.name)
        } catch (e: Exception) {
            CategorySorting.DEFAULT
        }
    }

    fun setTvCategorySorting(sorting: CategorySorting) {
        sharedPreferences.edit().putString(KEY_TV_SORTING, sorting.name).apply()
    }

    fun getVodCategorySorting(): CategorySorting {
        val name = sharedPreferences.getString(KEY_VOD_SORTING, CategorySorting.DEFAULT.name)
        return try {
            CategorySorting.valueOf(name ?: CategorySorting.DEFAULT.name)
        } catch (e: Exception) {
            CategorySorting.DEFAULT
        }
    }

    fun setVodCategorySorting(sorting: CategorySorting) {
        sharedPreferences.edit().putString(KEY_VOD_SORTING, sorting.name).apply()
    }

    fun getSeriesCategorySorting(): CategorySorting {
        val name = sharedPreferences.getString(KEY_SERIES_SORTING, CategorySorting.DEFAULT.name)
        return try {
            CategorySorting.valueOf(name ?: CategorySorting.DEFAULT.name)
        } catch (e: Exception) {
            CategorySorting.DEFAULT
        }
    }

    fun setSeriesCategorySorting(sorting: CategorySorting) {
        sharedPreferences.edit().putString(KEY_SERIES_SORTING, sorting.name).apply()
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
}
