package com.cstv.app.presentation.player.core

import com.cstv.app.domain.model.SeriesEpisode
import com.cstv.app.domain.model.SeriesStream
import com.cstv.app.domain.model.SeriesVersionCandidate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

/**
 * F39-R2/R3/R4 (étape 7) : orchestration pure de la version série jouée, extraite de
 * `SeriesPlayerScreen` précisément pour rendre testables les scénarios que la review notait
 * absents — préférence appliquée à l'ouverture et au binge, et cohérence d'une séquence
 * A → B (réussie) → C (échec) qui ne doit jamais restaurer A à la place de B.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SeriesVersionSwitchCoordinatorTest {

    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)

    private fun series(id: Int, linkKey: String = "key") = SeriesStream(
        seriesId = id, name = "Série $id", cover = null, rating = null, added = null, categoryId = "1", linkKey = linkKey
    )

    private fun episode(id: Int, seasonNum: Int = 1, episodeNum: Int = 1) = SeriesEpisode(
        id = id, episodeNum = episodeNum, title = "Ep", containerExtension = "mp4", plot = "", duration = "",
        releaseDate = "", seasonNum = seasonNum
    )

    private fun candidate(seriesId: Int, episodeId: Int, linkKey: String = "key") =
        SeriesVersionCandidate(series(seriesId, linkKey), episode(episodeId))

    private fun coordinator(
        initialSeriesId: Int,
        initialEpisode: SeriesEpisode,
        engine: FakeMediaVersionSwitchEngine,
        versionsEnabled: Boolean = true,
        initialSeriesName: String = "Série $initialSeriesId",
        resolvePreferred: suspend (Int, Int, Int) -> SeriesVersionCandidate? = { _, _, _ -> null },
        onPersistPreference: suspend (String, Int) -> Unit = { _, _ -> }
    ) = SeriesVersionSwitchCoordinator(
        initialSeriesId = initialSeriesId,
        initialEpisode = initialEpisode,
        initialSeriesName = initialSeriesName,
        versionsEnabled = versionsEnabled,
        switchController = MediaVersionSwitchController(engine),
        buildPlayUrl = { ep -> "https://x/${ep.id}.mp4" },
        buildCacheKey = { ep -> "cache-${ep.id}" },
        resolvePreferred = resolvePreferred,
        onPersistPreference = onPersistPreference
    )

    @Test
    fun `F39-R3 - A to B succeeds then B to C fails, rollback restores B not A`() = runTest {
        val episodeA = episode(id = 1)
        val engine = FakeMediaVersionSwitchEngine(
            scripts = listOf(
                FakeMediaVersionSwitchEngine.readyImmediately(), // A -> B : succès
                FakeMediaVersionSwitchEngine.neverResponds(), // B -> C : jamais Ready -> timeout -> rollback
                FakeMediaVersionSwitchEngine.readyImmediately() // rollback : B redevient Ready
            ),
            initialPositionMs = 5_000L
        )
        val coordinator = coordinator(initialSeriesId = 1, initialEpisode = episodeA, engine = engine)

        val outcomeAB = coordinator.switchTo(candidate(seriesId = 2, episodeId = 20))
        assertTrue(outcomeAB is SeriesVersionSwitchCoordinator.SwitchOutcome.Applied)
        assertEquals(2, coordinator.currentSeriesId)
        assertEquals(20, coordinator.currentEpisode.id)

        val outcomeBC = coordinator.switchTo(candidate(seriesId = 3, episodeId = 30))
        assertEquals(SeriesVersionSwitchCoordinator.SwitchOutcome.RolledBack, outcomeBC)
        // L'identité reste sur B après l'échec du rollback vers C, jamais retombée sur A.
        assertEquals(2, coordinator.currentSeriesId)
        assertEquals(20, coordinator.currentEpisode.id)
        // F39-R3 (cœur du constat de review) : la cible de rollback préparée par le contrôleur est
        // bien B (cache-20), jamais A (cache-1) — c'est justement ce que l'identité non atomique
        // de l'implémentation d'origine aurait cassé après un premier changement d'épisode.
        assertEquals(
            listOf("https://x/20.mp4", "https://x/30.mp4", "https://x/20.mp4"),
            engine.preparedTargets.map { it.mediaUrl }
        )
    }

    @Test
    fun `F39-R2 - opening the screen applies a memorized preference`() = runTest {
        val openedEpisode = episode(id = 1, seasonNum = 2, episodeNum = 5)
        val preferred = candidate(seriesId = 9, episodeId = 90)
        val engine = FakeMediaVersionSwitchEngine(scripts = emptyList())
        var resolveCalledWith: Triple<Int, Int, Int>? = null
        val coordinator = coordinator(
            initialSeriesId = 1,
            initialEpisode = openedEpisode,
            engine = engine,
            resolvePreferred = { openSeriesId, seasonNum, episodeNum ->
                resolveCalledWith = Triple(openSeriesId, seasonNum, episodeNum)
                preferred
            }
        )

        coordinator.resolveAndSet(openedEpisode.seasonNum, openedEpisode.episodeNum, openedEpisode)

        assertEquals(Triple(1, 2, 5), resolveCalledWith)
        assertEquals(9, coordinator.currentSeriesId)
        assertEquals(90, coordinator.currentEpisode.id)
    }

    @Test
    fun `F39-R2 - binge to the next episode re-resolves the memorized preference for it`() = runTest {
        val engine = FakeMediaVersionSwitchEngine(scripts = emptyList())
        val nextOnOpenedSeries = episode(id = 2, seasonNum = 1, episodeNum = 2)
        val preferredForNext = candidate(seriesId = 5, episodeId = 200)
        val coordinator = coordinator(
            initialSeriesId = 5,
            initialEpisode = episode(id = 100, seasonNum = 1, episodeNum = 1),
            engine = engine,
            resolvePreferred = { openSeriesId, seasonNum, episodeNum ->
                if (openSeriesId == 5 && seasonNum == 1 && episodeNum == 2) preferredForNext else null
            }
        )

        coordinator.resolveAndSet(nextOnOpenedSeries.seasonNum, nextOnOpenedSeries.episodeNum, nextOnOpenedSeries)

        assertEquals(5, coordinator.currentSeriesId)
        assertEquals(200, coordinator.currentEpisode.id)
    }

    @Test
    fun `F39-R2 - without a memorized preference, the fallback episode is used as-is`() = runTest {
        val fallback = episode(id = 2, seasonNum = 1, episodeNum = 2)
        val coordinator = coordinator(
            initialSeriesId = 5,
            initialEpisode = episode(id = 100),
            engine = FakeMediaVersionSwitchEngine(scripts = emptyList()),
            resolvePreferred = { _, _, _ -> null }
        )

        coordinator.resolveAndSet(fallback.seasonNum, fallback.episodeNum, fallback)

        assertEquals(5, coordinator.currentSeriesId)
        assertEquals(2, coordinator.currentEpisode.id)
    }

    @Test
    fun `F39-R2 - offline mode never triggers resolution, the fallback episode is applied as-is`() = runTest {
        val fallback = episode(id = 2, seasonNum = 1, episodeNum = 2)
        var resolveCalls = 0
        val coordinator = coordinator(
            initialSeriesId = 5,
            initialEpisode = episode(id = 100),
            engine = FakeMediaVersionSwitchEngine(scripts = emptyList()),
            versionsEnabled = false,
            resolvePreferred = { _, _, _ -> resolveCalls++; candidate(seriesId = 9, episodeId = 900) }
        )

        coordinator.resolveAndSet(fallback.seasonNum, fallback.episodeNum, fallback)

        assertEquals("aucune requête de résolution en mode hors ligne", 0, resolveCalls)
        assertEquals(5, coordinator.currentSeriesId)
        assertEquals(2, coordinator.currentEpisode.id)
    }

    @Test
    fun `a successful switch persists the series preference exactly once`() = runTest {
        val engine = FakeMediaVersionSwitchEngine(scripts = listOf(FakeMediaVersionSwitchEngine.readyImmediately()))
        var persisted: Pair<String, Int>? = null
        val coordinator = coordinator(
            initialSeriesId = 1,
            initialEpisode = episode(id = 1),
            engine = engine,
            onPersistPreference = { linkKey, seriesId -> persisted = linkKey to seriesId }
        )

        coordinator.switchTo(candidate(seriesId = 2, episodeId = 20, linkKey = "movie-key"))

        assertEquals("movie-key" to 2, persisted)
    }

    @Test
    fun `retour utilisateur 2026-08-18 - a successful switch updates the displayed series name`() = runTest {
        val engine = FakeMediaVersionSwitchEngine(scripts = listOf(FakeMediaVersionSwitchEngine.readyImmediately()))
        val coordinator = coordinator(
            initialSeriesId = 1,
            initialEpisode = episode(id = 1),
            initialSeriesName = "House Of The Dragon",
            engine = engine
        )

        coordinator.switchTo(SeriesVersionCandidate(series(2).copy(cleanTitle = "House of the Dragon (4K-DV)"), episode(20)))

        assertEquals("House of the Dragon (4K-DV)", coordinator.currentSeriesName)
    }

    @Test
    fun `retour utilisateur 2026-08-18 - a rolled back switch keeps the previous displayed series name`() = runTest {
        val engine = FakeMediaVersionSwitchEngine(
            scripts = listOf(FakeMediaVersionSwitchEngine.neverResponds(), FakeMediaVersionSwitchEngine.readyImmediately())
        )
        val coordinator = coordinator(
            initialSeriesId = 1,
            initialEpisode = episode(id = 1),
            initialSeriesName = "House Of The Dragon",
            engine = engine
        )

        coordinator.switchTo(SeriesVersionCandidate(series(2).copy(cleanTitle = "House of the Dragon (4K-DV)"), episode(20)))

        assertEquals("House Of The Dragon", coordinator.currentSeriesName)
    }

    @Test
    fun `a rolled back switch never persists the series preference`() = runTest {
        val engine = FakeMediaVersionSwitchEngine(
            scripts = listOf(
                FakeMediaVersionSwitchEngine.neverResponds(), // cible : jamais Ready -> timeout -> rollback
                FakeMediaVersionSwitchEngine.readyImmediately() // rollback : source précédente à nouveau prête
            )
        )
        var persisted: Pair<String, Int>? = null
        val coordinator = coordinator(
            initialSeriesId = 1,
            initialEpisode = episode(id = 1),
            engine = engine,
            onPersistPreference = { linkKey, seriesId -> persisted = linkKey to seriesId }
        )

        val outcome = coordinator.switchTo(candidate(seriesId = 2, episodeId = 20))

        assertEquals(SeriesVersionSwitchCoordinator.SwitchOutcome.RolledBack, outcome)
        assertNull(persisted)
        assertFalse("l'identité ne doit pas changer sur un rollback", coordinator.currentSeriesId == 2)
    }
}
