package com.cstv.app.domain.model

import com.cstv.app.domain.repository.SeriesRepository
import com.cstv.app.domain.repository.SeriesVersionPreferenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** F39 §8.2/§8.3, tâche 2/3 : le resolver n'élimine que les candidates sans épisode équivalent,
 *  et la préférence mémorisée pilote le choix par défaut avec repli paresseux sur l'obsolescence. */
class SeriesVersionResolverTest {

    private fun series(id: Int) = SeriesStream(
        seriesId = id, name = "Série $id", cover = null, rating = null, added = null, categoryId = "1"
    )

    private fun episode(id: Int, season: Int, ep: Int) = SeriesEpisode(
        id = id, episodeNum = ep, title = "E$ep", containerExtension = "mkv",
        plot = "", duration = "", releaseDate = "", seasonNum = season
    )

    private class FakeSeriesRepository(
        private val versions: List<SeriesStream>,
        private val episodesBySeriesId: Map<Int, SeriesEpisode>
    ) : SeriesRepository by UnsupportedSeriesRepository {
        override suspend fun getVersionsByLinkKey(linkKey: String, releaseYear: Int?) = versions
        override suspend fun getEpisodeBySeasonEpisode(seriesId: Int, seasonNum: Int, episodeNum: Int) =
            episodesBySeriesId[seriesId]?.takeIf { it.seasonNum == seasonNum && it.episodeNum == episodeNum }
    }

    private class FakePreferenceRepository(private var preferredSeriesId: Int?) : SeriesVersionPreferenceRepository {
        var cleared = false
            private set
        var savedPreferredSeriesId: Int? = null
            private set

        override suspend fun getPreferredSeriesId(linkKey: String) = preferredSeriesId
        override suspend fun setPreference(linkKey: String, preferredSeriesId: Int) {
            this.preferredSeriesId = preferredSeriesId
            savedPreferredSeriesId = preferredSeriesId
        }
        override suspend fun clearPreference(linkKey: String) {
            preferredSeriesId = null
            cleared = true
        }
    }

    private class FakeCategoryPreferenceRepository(private val preferences: Map<String, CategoryPreference>) : com.cstv.app.domain.repository.CategoryPreferenceRepository {
        override val changes: Flow<Unit> = flowOf(Unit)
        override suspend fun getPreferences(type: CategoryType) = preferences
        override suspend fun setHidden(type: CategoryType, categoryId: String, hidden: Boolean) {}
        override suspend fun saveOrder(type: CategoryType, orderedCategoryIds: List<String>) {}
    }

    // --- resolve() : tâche 2 ---

    @Test
    fun `a candidate series belonging to a hidden category is filtered out`() = runTest {
        val completeInVisibleCat = SeriesStream(
            seriesId = 1, name = "Série 1", cover = null, rating = null, added = null, categoryId = "visible"
        )
        val completeInHiddenCat = SeriesStream(
            seriesId = 2, name = "Série 2", cover = null, rating = null, added = null, categoryId = "hidden"
        )
        val repo = FakeSeriesRepository(
            versions = listOf(completeInVisibleCat, completeInHiddenCat),
            episodesBySeriesId = mapOf(
                1 to episode(101, season = 1, ep = 3),
                2 to episode(102, season = 1, ep = 3)
            )
        )
        val catPrefs = mapOf("hidden" to CategoryPreference("hidden", hidden = true, sortOrder = null))
        val fakeCatPrefRepo = FakeCategoryPreferenceRepository(catPrefs)
        val resolver = SeriesVersionResolver(repo, FakePreferenceRepository(null), fakeCatPrefRepo)

        val result = resolver.resolve(linkKey = "key", releaseYear = null, seasonNum = 1, episodeNum = 3)

        assertEquals(1, result.size)
        assertEquals(completeInVisibleCat, result.single().series)
        assertEquals(101, result.single().episode.id)
    }

    @Test
    fun `a candidate series without the equivalent episode is filtered out`() = runTest {
        val complete = series(1)
        val incomplete = series(2)
        val repo = FakeSeriesRepository(
            versions = listOf(complete, incomplete),
            episodesBySeriesId = mapOf(1 to episode(101, season = 1, ep = 3))
            // série 2 : aucun épisode S01E03 en cache -> filtrée.
        )
        val resolver = SeriesVersionResolver(repo, FakePreferenceRepository(null))

        val result = resolver.resolve(linkKey = "key", releaseYear = null, seasonNum = 1, episodeNum = 3)

        assertEquals(1, result.size)
        assertEquals(complete, result.single().series)
        assertEquals(101, result.single().episode.id)
    }

    @Test
    fun `blank link key resolves to no candidates without querying the repository`() = runTest {
        val repo = FakeSeriesRepository(versions = listOf(series(1)), episodesBySeriesId = emptyMap())
        val resolver = SeriesVersionResolver(repo, FakePreferenceRepository(null))

        assertTrue(resolver.resolve(linkKey = "", releaseYear = null, seasonNum = 1, episodeNum = 1).isEmpty())
    }

    @Test
    fun `no candidate at all resolves to an empty list, never a crash`() = runTest {
        val repo = FakeSeriesRepository(versions = emptyList(), episodesBySeriesId = emptyMap())
        val resolver = SeriesVersionResolver(repo, FakePreferenceRepository(null))

        assertTrue(resolver.resolve(linkKey = "key", releaseYear = 2020, seasonNum = 2, episodeNum = 5).isEmpty())
    }

    // --- resolvePreferred() : tâche 3, §8.3 ---

    @Test
    fun `a valid stored preference wins over the opened series`() = runTest {
        val opened = series(1)
        val preferred = series(2)
        val repo = FakeSeriesRepository(
            versions = listOf(opened, preferred),
            episodesBySeriesId = mapOf(1 to episode(101, 1, 1), 2 to episode(102, 1, 1))
        )
        val prefs = FakePreferenceRepository(preferredSeriesId = 2)
        val resolver = SeriesVersionResolver(repo, prefs)

        val result = resolver.resolvePreferred("key", null, seasonNum = 1, episodeNum = 1, openedSeriesId = 1)

        assertEquals(preferred, result?.series)
        assertTrue("une préférence toujours valide ne doit jamais être effacée", !prefs.cleared)
    }

    @Test
    fun `a stale preference is lazily cleared and falls back to the opened series`() = runTest {
        val opened = series(1)
        // série 2 (préférée) n'a plus l'épisode équivalent en cache -> candidate invalide.
        val repo = FakeSeriesRepository(
            versions = listOf(opened),
            episodesBySeriesId = mapOf(1 to episode(101, 1, 1))
        )
        val prefs = FakePreferenceRepository(preferredSeriesId = 2)
        val resolver = SeriesVersionResolver(repo, prefs)

        val result = resolver.resolvePreferred("key", null, seasonNum = 1, episodeNum = 1, openedSeriesId = 1)

        assertEquals(opened, result?.series)
        assertTrue("la préférence obsolète doit être supprimée paresseusement", prefs.cleared)
    }

    @Test
    fun `no stored preference falls back to the opened series among candidates`() = runTest {
        val opened = series(1)
        val other = series(2)
        val repo = FakeSeriesRepository(
            versions = listOf(other, opened),
            episodesBySeriesId = mapOf(1 to episode(101, 1, 1), 2 to episode(102, 1, 1))
        )
        val prefs = FakePreferenceRepository(preferredSeriesId = null)
        val resolver = SeriesVersionResolver(repo, prefs)

        val result = resolver.resolvePreferred("key", null, seasonNum = 1, episodeNum = 1, openedSeriesId = 1)

        assertEquals(opened, result?.series)
    }

    @Test
    fun `no valid candidate at all, including the opened series, resolves to null`() = runTest {
        val repo = FakeSeriesRepository(versions = emptyList(), episodesBySeriesId = emptyMap())
        val resolver = SeriesVersionResolver(repo, FakePreferenceRepository(null))

        assertNull(resolver.resolvePreferred("key", null, seasonNum = 1, episodeNum = 1, openedSeriesId = 1))
    }
}

/** Délégué minimal : toute méthode non stubbée par un test ne doit jamais être atteinte. */
private object UnsupportedSeriesRepository : SeriesRepository {
    private fun unsupported(): Nothing = throw UnsupportedOperationException("not stubbed by this test")
    override fun observeSeriesCategories(): Flow<List<SeriesCategory>> = flowOf(emptyList())
    override fun observeSeriesStreams(categoryId: String) = flowOf(emptyList<SeriesStream>())
    override fun getSeriesStreamsPaged(categoryId: String) = unsupported()
    override suspend fun getCachedSeriesCategories() = unsupported()
    override suspend fun getCachedSeriesStreams(categoryId: String) = unsupported()
    override suspend fun getCachedSeriesStreamsByYears(years: Set<Int>) = unsupported()
    override suspend fun syncSeriesCategories() = unsupported()
    override suspend fun syncSeriesStreams(categoryId: String) = unsupported()
    override suspend fun getSeriesDetails(seriesId: Int) = unsupported()
    override suspend fun enrichPendingSeries(maxBatches: Int) = unsupported()
    override suspend fun getCategoryCounts() = unsupported()
    override suspend fun hasCachedSeriesStreams() = unsupported()
    override suspend fun getReleaseYearBounds() = unsupported()
    override suspend fun getRelatedSeries(currentSeriesId: Int, genre: String?, limit: Int, excludedCategoryIds: Set<String>) = unsupported()
    override suspend fun getStreamById(seriesId: Int) = unsupported()
    override suspend fun getRecommendableSeriesItems() = unsupported()
    override suspend fun getStreamsByIds(seriesIds: List<Int>) = unsupported()
}
