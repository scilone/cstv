package com.cstv.app.presentation.home

import com.cstv.app.domain.model.FavoriteItem
import com.cstv.app.domain.model.LiveStream
import com.cstv.app.domain.model.PlaybackPosition
import com.cstv.app.domain.model.SeriesStream
import com.cstv.app.domain.model.TrendingCatalogItem
import com.cstv.app.domain.model.TrendingTitle
import com.cstv.app.domain.model.VodStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeInitialFocusTargetTest {
    private val trending = listOf(TrendingCatalogItem(TrendingTitle(1, "Film", true, 2024, null)))
    private val resume = listOf(PlaybackPosition(1, 1000L, 5000L, 0L))
    private val favorites = listOf(FavoriteItem(1, "movie", "Film", null, "10"))
    private val liveStreams = listOf(LiveStream(1, "Chaîne", null, null, 1, "10"))
    private val vodStreams = listOf(VodStream(1, "Film", null, null, null, "10"))
    private val seriesStreams = listOf(SeriesStream(1, "Série", null, null, null, "10"))

    @Test
    fun loadingOrMobileHasNoAutomaticTarget() {
        assertNull(HomeInitialFocusTarget.of(HomeState(isLoading = true), isTv = true))
        assertNull(HomeInitialFocusTarget.of(HomeState(), isTv = false))
    }

    @Test
    fun trendingWinsOverResumeWhenBothArePresent() {
        val state = HomeState(trendingList = trending, resumeWatchingList = resume)
        assertEquals(HomeFocusTarget.TRENDING, HomeInitialFocusTarget.of(state, isTv = true))
    }

    @Test
    fun emptyTrendingFallsBackToResume() {
        val state = HomeState(resumeWatchingList = resume, favoritesList = favorites)
        assertEquals(HomeFocusTarget.RESUME, HomeInitialFocusTarget.of(state, isTv = true))
    }

    @Test
    fun emptyTrendingAndResumeFallsBackToFavorites() {
        val state = HomeState(favoritesList = favorites, firstLiveStreams = liveStreams)
        assertEquals(HomeFocusTarget.FAVORITES, HomeInitialFocusTarget.of(state, isTv = true))
    }

    @Test
    fun emptyTrendingResumeAndFavoritesFallsBackToLiveTv() {
        val state = HomeState(firstLiveStreams = liveStreams, firstVodStreams = vodStreams)
        assertEquals(HomeFocusTarget.LIVETV, HomeInitialFocusTarget.of(state, isTv = true))
    }

    @Test
    fun onlyALateRowNonEmptyResolvesToThatRow() {
        val state = HomeState(recommendedSeries = seriesStreams)
        assertEquals(HomeFocusTarget.RECOMMENDED_SERIES, HomeInitialFocusTarget.of(state, isTv = true))
    }

    @Test
    fun entirelyEmptyStateHasNoTarget() {
        assertNull(HomeInitialFocusTarget.of(HomeState(), isTv = true))
    }
}
