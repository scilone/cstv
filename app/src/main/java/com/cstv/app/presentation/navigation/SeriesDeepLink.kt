package com.cstv.app.presentation.navigation

import android.content.Intent

/**
 * Deep link "notification de nouveaux épisodes" (F12) : extraits d'intent
 * réutilisant l'état hissé de `MainActivity` (`activeSeriesShow`) plutôt
 * qu'une route paramétrée — voir la décision technique F12 §8.7.
 */
object SeriesDeepLink {
    const val EXTRA_SERIES_ID = "extra_series_id"
    const val EXTRA_PROFILE_ID = "extra_profile_id"

    private const val NO_EXTRA = -1

    data class Target(val seriesId: Int, val profileId: Int)

    /** null si l'intent n'a pas les deux extras attendus (intent incomplet ou sans rapport). */
    fun extract(intent: Intent?): Target? {
        val seriesId = intent?.getIntExtra(EXTRA_SERIES_ID, NO_EXTRA) ?: NO_EXTRA
        val profileId = intent?.getIntExtra(EXTRA_PROFILE_ID, NO_EXTRA) ?: NO_EXTRA
        if (seriesId == NO_EXTRA || profileId == NO_EXTRA) return null
        return Target(seriesId, profileId)
    }
}
