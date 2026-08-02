package com.cstv.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeLabelTest {

    @Test
    fun format_padsSingleDigitSeasonAndEpisode() {
        assertEquals("S01 E03", EpisodeLabel.format(1, 3))
    }

    @Test
    fun format_keepsNumbersAboveNinetyNineIntact() {
        assertEquals("S10 E128", EpisodeLabel.format(10, 128))
    }

    @Test
    fun format_returnsNullWhenSeasonOrEpisodeIsUnknown() {
        assertNull(EpisodeLabel.format(null, 3))
        assertNull(EpisodeLabel.format(1, null))
        assertNull(EpisodeLabel.format(null, null))
    }

    private fun position(
        streamId: Int,
        seriesId: Int? = null,
        seasonNum: Int? = null,
        episodeNum: Int? = null
    ) = PlaybackPosition(
        streamId = streamId,
        positionMs = 0L,
        durationMs = 0L,
        lastAccessedAt = 0L,
        seriesId = seriesId,
        seasonNum = seasonNum,
        episodeNum = episodeNum
    )

    @Test
    fun buildResumeLabels_seriesPositionProducesEntryUnderProvidedKey() {
        // B18 Review R1 : le test précédent ne construisait jamais réellement
        // cette table — celui-ci exerce le mapNotNull et la clé transmise.
        val positions = listOf(position(streamId = 501, seriesId = 42, seasonNum = 1, episodeNum = 3))

        val labels = EpisodeLabel.buildResumeLabels(positions) { it.seriesId ?: it.streamId }

        assertEquals(mapOf(42 to "S01 E03"), labels)
    }

    @Test
    fun buildResumeLabels_moviePositionWithoutEpisodeMetadataProducesNoEntry() {
        val positions = listOf(position(streamId = 7))

        val labels = EpisodeLabel.buildResumeLabels(positions) { it.streamId }

        assertTrue(labels.isEmpty())
    }

    @Test
    fun buildResumeLabels_incompleteSeriesMetadataProducesNoEntry() {
        // Saison connue, épisode inconnu : EpisodeLabel.format renvoie null,
        // aucune entrée ne doit apparaître pour cette position.
        val positions = listOf(position(streamId = 8, seriesId = 9, seasonNum = 2, episodeNum = null))

        val labels = EpisodeLabel.buildResumeLabels(positions) { it.seriesId ?: it.streamId }

        assertTrue(labels.isEmpty())
    }

    @Test
    fun buildResumeLabels_mixedListOnlyKeepsPositionsWithKnownEpisode() {
        val positions = listOf(
            position(streamId = 1, seriesId = 10, seasonNum = 1, episodeNum = 1),
            position(streamId = 2), // film : pas de saison/épisode
            position(streamId = 3, seriesId = 30, seasonNum = 4, episodeNum = 12)
        )

        val labels = EpisodeLabel.buildResumeLabels(positions) { it.seriesId ?: it.streamId }

        assertEquals(mapOf(10 to "S01 E01", 30 to "S04 E12"), labels)
    }

    @Test
    fun buildResumeLabels_fallsBackToStreamIdWhenSeriesIdIsNull() {
        // Cas des flux "série" dont le seriesId n'est pas encore résolu :
        // la clé de secours (streamId) est celle utilisée par SeriesScreen.kt.
        val positions = listOf(position(streamId = 99, seriesId = null, seasonNum = 1, episodeNum = 2))

        val labels = EpisodeLabel.buildResumeLabels(positions) { it.seriesId ?: it.streamId }

        assertEquals(mapOf(99 to "S01 E02"), labels)
    }
}
