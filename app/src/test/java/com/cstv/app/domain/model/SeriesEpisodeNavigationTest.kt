package com.cstv.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

class SeriesEpisodeNavigationTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)


    private fun ep(season: Int, num: Int, id: Int = season * 100 + num) = SeriesEpisode(
        id = id,
        episodeNum = num,
        title = "S${season}E$num",
        containerExtension = "mp4",
        plot = "",
        duration = "",
        releaseDate = "",
        seasonNum = season
    )

    private val episodes = mapOf(
        1 to listOf(ep(1, 1), ep(1, 2), ep(1, 3)),
        2 to listOf(ep(2, 1), ep(2, 2))
    )

    @Test
    fun test_nextInSameSeason() {
        val next = computeNextEpisode(episodes, ep(1, 1))
        assertEquals(2, next?.episodeNum)
        assertEquals(1, next?.seasonNum)
    }

    @Test
    fun test_lastEpisodeOfSeason_movesToNextSeasonFirstEpisode() {
        val next = computeNextEpisode(episodes, ep(1, 3))
        assertEquals(1, next?.episodeNum)
        assertEquals(2, next?.seasonNum)
    }

    @Test
    fun test_lastEpisodeOfLastSeason_returnsNull() {
        assertNull(computeNextEpisode(episodes, ep(2, 2)))
    }

    @Test
    fun test_emptyMap_returnsNull() {
        assertNull(computeNextEpisode(emptyMap(), ep(1, 1)))
    }

    @Test
    fun test_nextSeasonPickedByLowestEpisodeNum_notFirstInList() {
        // La saison suivante est renvoyée dans un ordre non trié : on doit
        // choisir l'épisode de plus petit numéro, pas le premier de la liste.
        val unordered = mapOf(
            1 to listOf(ep(1, 1)),
            2 to listOf(ep(2, 3), ep(2, 1), ep(2, 2))
        )
        val next = computeNextEpisode(unordered, ep(1, 1))
        assertEquals(1, next?.episodeNum)
        assertEquals(2, next?.seasonNum)
    }

    @Test
    fun test_gapInSeasonNumbers_jumpsToSmallestGreaterSeason() {
        // Trou de numérotation : après la saison 1, la plus petite saison
        // strictement supérieure présente est la 3.
        val gapped = mapOf(
            1 to listOf(ep(1, 1)),
            3 to listOf(ep(3, 1), ep(3, 2))
        )
        val next = computeNextEpisode(gapped, ep(1, 1))
        assertEquals(1, next?.episodeNum)
        assertEquals(3, next?.seasonNum)
    }

    @Test
    fun test_missingNextEpisodeNumberInSameSeason_fallsBackToNextSeason() {
        // episodeNum+1 absent dans la saison courante (ex. numérotation non
        // contiguë) → on bascule sur la saison suivante plutôt que de sauter
        // un épisode dans la même saison.
        val nonContiguous = mapOf(
            1 to listOf(ep(1, 1), ep(1, 5)),
            2 to listOf(ep(2, 1))
        )
        val next = computeNextEpisode(nonContiguous, ep(1, 1))
        assertEquals(1, next?.episodeNum)
        assertEquals(2, next?.seasonNum)
    }

    @Test
    fun test_previousInSameSeason() {
        val prev = computePreviousEpisode(episodes, ep(1, 2))
        assertEquals(1, prev?.episodeNum)
        assertEquals(1, prev?.seasonNum)
    }

    @Test
    fun test_firstEpisodeOfSeason_movesToPreviousSeasonLastEpisode() {
        val prev = computePreviousEpisode(episodes, ep(2, 1))
        assertEquals(3, prev?.episodeNum)
        assertEquals(1, prev?.seasonNum)
    }

    @Test
    fun test_firstEpisodeOfFirstSeason_returnsNull() {
        assertNull(computePreviousEpisode(episodes, ep(1, 1)))
    }

    @Test
    fun test_previous_emptyMap_returnsNull() {
        assertNull(computePreviousEpisode(emptyMap(), ep(1, 1)))
    }

    @Test
    fun test_previousSeasonPickedByHighestEpisodeNum_notFirstInList() {
        val unordered = mapOf(
            1 to listOf(ep(1, 3), ep(1, 1), ep(1, 2)),
            2 to listOf(ep(2, 1))
        )
        val prev = computePreviousEpisode(unordered, ep(2, 1))
        assertEquals(3, prev?.episodeNum)
        assertEquals(1, prev?.seasonNum)
    }

    @Test
    fun test_previous_gapInSeasonNumbers_jumpsToLargestLowerSeason() {
        val gapped = mapOf(
            1 to listOf(ep(1, 1), ep(1, 2)),
            3 to listOf(ep(3, 1))
        )
        val prev = computePreviousEpisode(gapped, ep(3, 1))
        assertEquals(2, prev?.episodeNum)
        assertEquals(1, prev?.seasonNum)
    }

    @Test
    fun test_previous_missingEpisodeNumberInSameSeason_fallsBackToPreviousSeason() {
        val nonContiguous = mapOf(
            1 to listOf(ep(1, 1)),
            2 to listOf(ep(2, 1), ep(2, 5))
        )
        val prev = computePreviousEpisode(nonContiguous, ep(2, 5))
        assertEquals(1, prev?.episodeNum)
        assertEquals(1, prev?.seasonNum)
    }
}
