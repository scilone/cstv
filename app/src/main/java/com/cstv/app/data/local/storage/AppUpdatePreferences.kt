package com.cstv.app.data.local.storage

import android.content.Context
import android.content.SharedPreferences
import com.cstv.app.domain.model.AppUpdateRelease
import com.cstv.app.domain.model.SemanticVersion
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistance locale des décisions de mise à jour in-app (F35).
 *
 * Fichier `SharedPreferences` séparé de [SettingsManager] et de tout profil :
 * ces valeurs sont **strictement locales à l'appareil**, jamais synchronisées
 * par F34 (§4.5), sans quoi la règle « 1× par jour et par appareil » (RG2)
 * serait violée.
 */
@Singleton
class AppUpdatePreferences @Inject constructor(context: Context) {

    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    companion object {
        private const val PREFS_NAME = "app_update_prefs"
        private const val KEY_LAST_AUTOMATIC_CHECK_AT = "last_automatic_check_at"
        private const val KEY_POSTPONED_VERSION_CODE = "postponed_version_code"
        private const val KEY_POSTPONED_UNTIL = "postponed_until"
        private const val KEY_IGNORED_VERSION_CODE = "ignored_version_code"
        private const val KEY_KNOWN_RELEASE_MAJOR = "known_release_major"
        private const val KEY_KNOWN_RELEASE_MINOR = "known_release_minor"
        private const val KEY_KNOWN_RELEASE_PATCH = "known_release_patch"
        private const val KEY_KNOWN_RELEASE_URL = "known_release_url"
        private const val KEY_KNOWN_RELEASE_ASSET_NAME = "known_release_asset_name"
        private const val KEY_KNOWN_RELEASE_ASSET_SIZE = "known_release_asset_size"
        private const val ABSENT = -1L
    }

    fun getLastAutomaticCheckAt(): Long? =
        sharedPreferences.getLong(KEY_LAST_AUTOMATIC_CHECK_AT, ABSENT).takeIf { it != ABSENT }

    fun setLastAutomaticCheckAt(timestampMillis: Long) {
        sharedPreferences.edit().putLong(KEY_LAST_AUTOMATIC_CHECK_AT, timestampMillis).apply()
    }

    fun getPostponedVersionCode(): Int? =
        sharedPreferences.getInt(KEY_POSTPONED_VERSION_CODE, ABSENT.toInt()).takeIf { it != ABSENT.toInt() }

    fun getPostponedUntilMillis(): Long? =
        sharedPreferences.getLong(KEY_POSTPONED_UNTIL, ABSENT).takeIf { it != ABSENT }

    /** RG4 : reporte l'invitation pour ce `versionCode` jusqu'à [untilMillis]. */
    fun setPostponed(versionCode: Int, untilMillis: Long) {
        sharedPreferences.edit()
            .putInt(KEY_POSTPONED_VERSION_CODE, versionCode)
            .putLong(KEY_POSTPONED_UNTIL, untilMillis)
            .apply()
    }

    fun getIgnoredVersionCode(): Int? =
        sharedPreferences.getInt(KEY_IGNORED_VERSION_CODE, ABSENT.toInt()).takeIf { it != ABSENT.toInt() }

    /** RG5 : masque définitivement l'invitation pour ce seul `versionCode`. */
    fun setIgnoredVersionCode(versionCode: Int) {
        sharedPreferences.edit().putInt(KEY_IGNORED_VERSION_CODE, versionCode).apply()
    }

    /**
     * Dernière Release **majeure** connue (§4.5) : permet de réafficher une
     * mise à jour obligatoire à chaque démarrage sans nouvel appel réseau
     * (RG7). N'est jamais écrite pour une Release facultative.
     */
    fun getKnownRelease(): AppUpdateRelease? {
        val major = sharedPreferences.getInt(KEY_KNOWN_RELEASE_MAJOR, ABSENT.toInt())
        if (major == ABSENT.toInt()) return null
        val minor = sharedPreferences.getInt(KEY_KNOWN_RELEASE_MINOR, 0)
        val patch = sharedPreferences.getInt(KEY_KNOWN_RELEASE_PATCH, 0)
        val url = sharedPreferences.getString(KEY_KNOWN_RELEASE_URL, null) ?: return null
        val assetName = sharedPreferences.getString(KEY_KNOWN_RELEASE_ASSET_NAME, null) ?: return null
        val assetSize = sharedPreferences.getLong(KEY_KNOWN_RELEASE_ASSET_SIZE, ABSENT)
        if (assetSize == ABSENT) return null
        return AppUpdateRelease(
            version = SemanticVersion(major, minor, patch),
            assetDownloadUrl = url,
            assetName = assetName,
            assetSizeBytes = assetSize
        )
    }

    fun saveKnownRelease(release: AppUpdateRelease) {
        sharedPreferences.edit()
            .putInt(KEY_KNOWN_RELEASE_MAJOR, release.version.major)
            .putInt(KEY_KNOWN_RELEASE_MINOR, release.version.minor)
            .putInt(KEY_KNOWN_RELEASE_PATCH, release.version.patch)
            .putString(KEY_KNOWN_RELEASE_URL, release.assetDownloadUrl)
            .putString(KEY_KNOWN_RELEASE_ASSET_NAME, release.assetName)
            .putLong(KEY_KNOWN_RELEASE_ASSET_SIZE, release.assetSizeBytes)
            .apply()
    }

    fun clearKnownRelease() {
        sharedPreferences.edit()
            .remove(KEY_KNOWN_RELEASE_MAJOR)
            .remove(KEY_KNOWN_RELEASE_MINOR)
            .remove(KEY_KNOWN_RELEASE_PATCH)
            .remove(KEY_KNOWN_RELEASE_URL)
            .remove(KEY_KNOWN_RELEASE_ASSET_NAME)
            .remove(KEY_KNOWN_RELEASE_ASSET_SIZE)
            .apply()
    }
}
