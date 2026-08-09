package com.cstv.app.presentation.series

import com.cstv.app.domain.model.SeriesDetails
import com.cstv.app.domain.model.SeriesEpisode
import com.cstv.app.domain.model.SeriesSeason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesDetailsTvLayoutTest {

    @Test
    fun `season numbers merge metadata and episode keys in ascending order`() {
        val details = details(
            seasons = listOf(season(3), season(1)),
            episodes = mapOf(2 to listOf(episode(21, 2, 1)), 1 to listOf(episode(11, 1, 1)))
        )

        assertEquals(listOf(1, 2, 3), tvSeriesSeasonNumbers(details))
    }

    @Test
    fun `initial target is first episode by season then episode when there is no resume`() {
        val details = details(
            seasons = listOf(season(2), season(1)),
            episodes = mapOf(
                2 to listOf(episode(22, 2, 2), episode(21, 2, 1)),
                1 to listOf(episode(12, 1, 2), episode(11, 1, 1))
            )
        )

        val target = tvSeriesPlaybackTarget(details)

        assertFalse(target.isResume)
        assertEquals(11, target.episode?.id)
    }

    @Test
    fun `most recently accessed incomplete episode wins over first episode`() {
        val older = episode(11, 1, 1, resume = 10_000L, duration = 60_000L, accessedAt = 100L)
        val recent = episode(22, 2, 2, resume = 20_000L, duration = 60_000L, accessedAt = 200L)
        val complete = episode(23, 2, 3, resume = 59_000L, duration = 60_000L, accessedAt = 300L)
        val details = details(episodes = mapOf(1 to listOf(older), 2 to listOf(recent, complete)))

        val target = tvSeriesPlaybackTarget(details)

        assertTrue(target.isResume)
        assertEquals(22, target.episode?.id)
    }

    @Test
    fun `progress is absent without duration and bounded otherwise`() {
        assertEquals(0f, tvSeriesProgressFraction(null), 0.001f)
        assertEquals(0f, tvSeriesProgressFraction(episode(1, 1, 1, resume = 10L, duration = 0L)), 0.001f)
        assertEquals(1f, tvSeriesProgressFraction(episode(1, 1, 1, resume = 200L, duration = 100L)), 0.001f)
    }

    @Test
    fun `related shift is zero without a row and reveals only hidden height`() {
        assertEquals(0f, tvSeriesRelatedShiftPx(984f, 0f, 24f, 1080f), 0.001f)
        assertEquals(128f, tvSeriesRelatedShiftPx(984f, 200f, 24f, 1080f), 0.001f)
    }

    @Test
    fun `the preselected season follows the most recently accessed episode`() {
        val details = details(
            seasons = listOf(season(1), season(2), season(3)),
            episodes = mapOf(
                1 to listOf(episode(11, 1, 1, accessedAt = 100L)),
                3 to listOf(episode(31, 3, 1, accessedAt = 300L))
            )
        )

        assertEquals(3, tvSeriesInitialSeason(details))
    }

    @Test
    fun `a finished episode still decides the preselected season`() {
        // Position remise à zéro en fin de lecture : c'est `lastAccessedAt`,
        // et lui seul, qui désigne la dernière saison suivie.
        val details = details(
            seasons = listOf(season(1), season(2)),
            episodes = mapOf(
                1 to listOf(episode(11, 1, 1, accessedAt = 100L)),
                2 to listOf(episode(21, 2, 1, accessedAt = 200L, watched = true))
            )
        )

        assertEquals(2, tvSeriesInitialSeason(details))
    }

    @Test
    fun `the first season is preselected without any viewing history`() {
        val details = details(
            seasons = listOf(season(2), season(1)),
            episodes = mapOf(1 to listOf(episode(11, 1, 1)), 2 to listOf(episode(21, 2, 1)))
        )

        assertEquals(1, tvSeriesInitialSeason(details))
        assertNull(tvSeriesInitialSeason(details()))
    }

    @Test
    fun `a viewing history on a season the catalogue no longer lists falls back to the first one`() {
        val details = details(
            seasons = listOf(season(1)),
            episodes = mapOf(1 to listOf(episode(11, 1, 1, accessedAt = 100L)))
        )
        val stale = details.copy(
            episodes = details.episodes + (9 to listOf(episode(91, 9, 1, accessedAt = 900L)))
        )

        // La saison 9 est bien exposée par `tvSeriesSeasonNumbers` dès qu'elle
        // porte des épisodes ; c'est l'absence totale de saison qui doit se
        // replier, pas une saison connue par ses seuls épisodes.
        assertEquals(9, tvSeriesInitialSeason(stale))
        assertEquals(1, tvSeriesInitialSeason(details))
    }

    @Test
    fun `a watched episode shows a full timeline even without a resume position`() {
        val watched = episode(1, 1, 1, watched = true)

        assertEquals(1f, tvSeriesEpisodeProgress(watched), 0.001f)
        assertNull(tvSeriesRemainingDuration(watched))
        assertEquals(TvSeriesEpisodeStatus.WATCHED, tvSeriesEpisodeStatus(watched))
    }

    @Test
    fun `episode status reports time only and never mixes duration with watch state`() {
        assertEquals(
            TvSeriesEpisodeStatus.REMAINING,
            tvSeriesEpisodeStatus(episode(1, 1, 1, resume = 10_000L, duration = 60_000L))
        )
        assertEquals(
            TvSeriesEpisodeStatus.DURATION,
            tvSeriesEpisodeStatus(episode(1, 1, 1))
        )
        // "00:00" est la valeur de repli des DTO Xtream : absence de durée, pas
        // une durée nulle — et surtout pas un état de visionnage.
        assertEquals(
            TvSeriesEpisodeStatus.NONE,
            tvSeriesEpisodeStatus(episode(1, 1, 1, durationLabel = "00:00"))
        )
        assertEquals(
            TvSeriesEpisodeStatus.NONE,
            tvSeriesEpisodeStatus(episode(1, 1, 1, durationLabel = ""))
        )
    }

    @Test
    fun `empty details has no playable target`() {
        val target = tvSeriesPlaybackTarget(details())

        assertFalse(target.isResume)
        assertNull(target.episode)
    }

    @Test
    fun `panel transitions keep the hero episodes and related routes explicit`() {
        assertEquals(
            TvSeriesDetailsSection.EPISODES,
            tvSeriesSectionAfter(
                TvSeriesDetailsSection.HERO,
                TvSeriesDetailsTransition.HERO_TO_EPISODES,
                hasRelatedTitles = false
            )
        )
        assertEquals(
            TvSeriesDetailsSection.RELATED,
            tvSeriesSectionAfter(
                TvSeriesDetailsSection.EPISODES,
                TvSeriesDetailsTransition.EPISODES_TO_RELATED,
                hasRelatedTitles = true
            )
        )
        assertEquals(
            TvSeriesDetailsSection.EPISODES,
            tvSeriesSectionAfter(
                TvSeriesDetailsSection.RELATED,
                TvSeriesDetailsTransition.RELATED_TO_EPISODES,
                hasRelatedTitles = true
            )
        )
        assertEquals(
            TvSeriesDetailsSection.HERO,
            tvSeriesSectionAfter(
                TvSeriesDetailsSection.EPISODES,
                TvSeriesDetailsTransition.EPISODES_TO_HERO,
                hasRelatedTitles = true
            )
        )
    }

    @Test
    fun `related transition is ignored when the series has no related titles`() {
        assertEquals(
            TvSeriesDetailsSection.EPISODES,
            tvSeriesSectionAfter(
                TvSeriesDetailsSection.EPISODES,
                TvSeriesDetailsTransition.EPISODES_TO_RELATED,
                hasRelatedTitles = false
            )
        )
    }

    @Test
    fun `episodes focus enters a pill then first episode and remains on an empty season`() {
        assertEquals(
            TvSeriesEpisodesFocusTarget.SEASON_PILL,
            tvSeriesEpisodesEntryFocusTarget(
                hasSeasons = true,
                hasEpisodes = true,
                returnToLastEpisode = false
            )
        )
        assertEquals(
            TvSeriesEpisodesFocusTarget.FIRST_EPISODE,
            tvSeriesSeasonDownFocusTarget(hasEpisodes = true)
        )
        assertEquals(
            TvSeriesEpisodesFocusTarget.SEASON_PILL,
            tvSeriesSeasonDownFocusTarget(hasEpisodes = false)
        )
    }

    @Test
    fun `related return targets the last episode and a series without seasons targets its empty state`() {
        assertEquals(
            TvSeriesEpisodesFocusTarget.LAST_EPISODE,
            tvSeriesEpisodesEntryFocusTarget(
                hasSeasons = true,
                hasEpisodes = true,
                returnToLastEpisode = true
            )
        )
        assertEquals(
            TvSeriesEpisodesFocusTarget.EMPTY_SERIES,
            tvSeriesEpisodesEntryFocusTarget(
                hasSeasons = false,
                hasEpisodes = false,
                returnToLastEpisode = false
            )
        )
    }

    @Test
    fun `remaining duration is calculated from the actual resume position`() {
        assertEquals("42 min", tvSeriesRemainingDuration(episode(1, 1, 1, resume = 18 * 60_000L, duration = 60 * 60_000L)))
        assertEquals("1h05", tvSeriesRemainingDuration(episode(1, 1, 1, resume = 55 * 60_000L, duration = 2 * 60 * 60_000L)))
        assertNull(tvSeriesRemainingDuration(episode(1, 1, 1, resume = 0L, duration = 60_000L)))
    }

    private fun details(
        seasons: List<SeriesSeason> = emptyList(),
        episodes: Map<Int, List<SeriesEpisode>> = emptyMap()
    ) = SeriesDetails(
        seriesId = 42,
        name = "Series",
        cover = null,
        rating = null,
        seasons = seasons,
        episodes = episodes
    )

    private fun season(number: Int) = SeriesSeason(number, "Saison $number", 0, null)

    private fun episode(
        id: Int,
        season: Int,
        number: Int,
        resume: Long = 0L,
        duration: Long = 0L,
        accessedAt: Long = 0L,
        watched: Boolean = false,
        durationLabel: String = "42 min"
    ) = SeriesEpisode(
        id = id,
        episodeNum = number,
        title = "Episode $number",
        containerExtension = "mp4",
        plot = "",
        duration = durationLabel,
        releaseDate = "",
        resumePositionMs = resume,
        durationMs = duration,
        lastAccessedAt = accessedAt,
        seasonNum = season,
        watched = watched
    )
}
