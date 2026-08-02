package com.cstv.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewEpisodeDetectorTest {

    private fun state(lastKnown: EpisodeRef, lastNotified: EpisodeRef? = null) =
        SeriesWatchState(profileId = 1, seriesId = 10, lastKnown = lastKnown, lastNotified = lastNotified)

    private fun position(
        season: Int,
        episode: Int,
        positionMs: Long,
        durationMs: Long
    ) = com.cstv.app.domain.model.PlaybackPosition(
        streamId = 1,
        positionMs = positionMs,
        durationMs = durationMs,
        lastAccessedAt = 0L,
        seriesId = 10,
        episodeNum = episode,
        seasonNum = season
    )

    @Test
    fun firstPass_withoutState_remembersWithoutNotifying() {
        val decision = NewEpisodeDetector.decide(
            stored = null,
            latestCompleted = EpisodeRef(1, 5),
            latestAvailable = EpisodeRef(1, 5)
        )
        assertEquals(NewEpisodeDetector.Decision.Remember(EpisodeRef(1, 5)), decision)
    }

    @Test
    fun finishedSeries_withOneNewEpisode_notifies() {
        val decision = NewEpisodeDetector.decide(
            stored = state(lastKnown = EpisodeRef(1, 5)),
            latestCompleted = EpisodeRef(1, 5),
            latestAvailable = EpisodeRef(1, 6)
        )
        assertEquals(NewEpisodeDetector.Decision.Notify(EpisodeRef(1, 6)), decision)
    }

    @Test
    fun finishedSeries_withThreeNewEpisodes_notifiesOnceWithLatest() {
        val decision = NewEpisodeDetector.decide(
            stored = state(lastKnown = EpisodeRef(1, 5)),
            latestCompleted = EpisodeRef(1, 5),
            latestAvailable = EpisodeRef(1, 8)
        )
        assertEquals(NewEpisodeDetector.Decision.Notify(EpisodeRef(1, 8)), decision)
    }

    @Test
    fun nextRun_withoutNewEpisode_remembersWithoutDuplicateNotify() {
        val decision = NewEpisodeDetector.decide(
            stored = state(lastKnown = EpisodeRef(1, 6)),
            latestCompleted = EpisodeRef(1, 6),
            latestAvailable = EpisodeRef(1, 6)
        )
        assertEquals(NewEpisodeDetector.Decision.Remember(EpisodeRef(1, 6)), decision)
    }

    @Test
    fun nextRun_withSameNewEpisodesAlreadyNotified_doesNotNotifyAgain() {
        val decision = NewEpisodeDetector.decide(
            stored = state(lastKnown = EpisodeRef(1, 5), lastNotified = EpisodeRef(1, 6)),
            latestCompleted = EpisodeRef(1, 5),
            latestAvailable = EpisodeRef(1, 6)
        )
        assertTrue(decision is NewEpisodeDetector.Decision.Remember)
    }

    @Test
    fun lastEpisode_stillInProgress_doesNotNotify() {
        // positionMs < durationMs - threshold -> pas terminé, donc absent de latestCompleted
        val positions = listOf(position(season = 1, episode = 6, positionMs = 1_000L, durationMs = 100_000L))
        val completed = NewEpisodeDetector.latestCompleted(positions)
        val decision = NewEpisodeDetector.decide(
            stored = state(lastKnown = EpisodeRef(1, 5)),
            latestCompleted = completed,
            latestAvailable = EpisodeRef(1, 6)
        )
        assertEquals(null, completed)
        assertTrue(decision is NewEpisodeDetector.Decision.Remember)
    }

    @Test
    fun zeroDuration_isNeverConsideredCompleted() {
        val positions = listOf(position(season = 1, episode = 5, positionMs = 0L, durationMs = 0L))
        assertEquals(null, NewEpisodeDetector.latestCompleted(positions))
    }

    @Test
    fun newSeason_afterLastEpisodeOfPreviousSeasonCompleted_notifies() {
        val decision = NewEpisodeDetector.decide(
            stored = state(lastKnown = EpisodeRef(1, 10)),
            latestCompleted = EpisodeRef(1, 10),
            latestAvailable = EpisodeRef(2, 1)
        )
        assertEquals(NewEpisodeDetector.Decision.Notify(EpisodeRef(2, 1)), decision)
    }

    @Test
    fun emptyOrMissingDetail_skipsWithoutChangingState() {
        val decision = NewEpisodeDetector.decide(
            stored = state(lastKnown = EpisodeRef(1, 5)),
            latestCompleted = EpisodeRef(1, 5),
            latestAvailable = null
        )
        assertEquals(NewEpisodeDetector.Decision.Skip, decision)
    }

    @Test
    fun episodeRemovedFromCatalog_doesNotReNotify() {
        // latestAvailable recule sous lastNotified : ne redéclenche pas de Notify.
        val decision = NewEpisodeDetector.decide(
            stored = state(lastKnown = EpisodeRef(1, 8), lastNotified = EpisodeRef(1, 8)),
            latestCompleted = EpisodeRef(1, 6),
            latestAvailable = EpisodeRef(1, 7)
        )
        assertTrue(decision is NewEpisodeDetector.Decision.Remember)
    }

    @Test
    fun unfinishedSeries_doesNotNotify() {
        val decision = NewEpisodeDetector.decide(
            stored = state(lastKnown = EpisodeRef(1, 5)),
            latestCompleted = EpisodeRef(1, 3),
            latestAvailable = EpisodeRef(1, 6)
        )
        assertTrue(decision is NewEpisodeDetector.Decision.Remember)
    }
}
