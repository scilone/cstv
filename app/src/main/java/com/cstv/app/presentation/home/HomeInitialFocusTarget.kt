package com.cstv.app.presentation.home

/**
 * Ordered fallback used when Home has no TV Hero to receive the initial focus.
 * No DOWNLOADS entry: the downloads row only renders on mobile
 * (`HomeScreen.kt`, `if (!isTv && …)`), and [HomeInitialFocusTarget.of] never
 * returns for `isTv = false`.
 */
enum class HomeFocusTarget { TRENDING, RESUME, FAVORITES, LIVETV, VOD, TOP_MOVIES, RECOMMENDED_MOVIES, SERIES, TOP_SERIES, RECOMMENDED_SERIES }

object HomeInitialFocusTarget {
    fun of(state: HomeState, isTv: Boolean): HomeFocusTarget? = when {
        !isTv || state.isLoading -> null
        state.trendingList.isNotEmpty() -> HomeFocusTarget.TRENDING
        state.resumeWatchingList.isNotEmpty() -> HomeFocusTarget.RESUME
        state.favoritesList.isNotEmpty() -> HomeFocusTarget.FAVORITES
        state.firstLiveStreams.isNotEmpty() -> HomeFocusTarget.LIVETV
        state.firstVodStreams.isNotEmpty() -> HomeFocusTarget.VOD
        !state.popularTopVodStreams.isNullOrEmpty() -> HomeFocusTarget.TOP_MOVIES
        state.recommendedMovies.isNotEmpty() -> HomeFocusTarget.RECOMMENDED_MOVIES
        state.firstSeriesStreams.isNotEmpty() -> HomeFocusTarget.SERIES
        !state.popularTopSeriesStreams.isNullOrEmpty() -> HomeFocusTarget.TOP_SERIES
        state.recommendedSeries.isNotEmpty() -> HomeFocusTarget.RECOMMENDED_SERIES
        else -> null
    }
}
