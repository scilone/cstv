package com.cstv.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.cstv.app.data.remote.api.CstvCatalogApiService
import com.cstv.app.data.remote.dto.CatalogItemDto
import com.cstv.app.data.remote.dto.CatalogItemsResponseDto
import com.google.gson.Gson
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PopularRepositoryImplTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)

    /** Context stubbé pour le cache produit + la purge paresseuse du cache legacy TMDB. */
    private fun contextWithPrefs(prefs: SharedPreferences = fakePrefs()): Context {
        val context = mock<Context>()
        val legacyPrefs = fakePrefs()
        whenever(context.getSharedPreferences("catalog_popular_cache", Context.MODE_PRIVATE)).thenReturn(prefs)
        whenever(context.getSharedPreferences("tmdb_popular_cache", Context.MODE_PRIVATE)).thenReturn(legacyPrefs)
        return context
    }

    private fun fakePrefs(): SharedPreferences {
        val prefs = mock<SharedPreferences>()
        val editor = mock<SharedPreferences.Editor>()
        whenever(prefs.edit()).thenReturn(editor)
        whenever(editor.clear()).thenReturn(editor)
        whenever(editor.putString(org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(editor)
        whenever(editor.putLong(org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(editor)
        return prefs
    }

    @Test
    fun getPopularMovies_readsProductPages() = runTest {
        val api = mock<CstvCatalogApiService>()
        (1..3).forEach { page -> whenever(api.popular("movie", page)).thenReturn(CatalogItemsResponseDto(listOf(CatalogItemDto(id = "movie:$page", kind = "movie", title = "Film $page", releaseYear = 2024)))) }
        val result = PopularRepositoryImpl(contextWithPrefs(), api, Gson()).getPopularMovies()
        assertEquals(listOf("movie:1", "movie:2", "movie:3"), result.map { it.canonicalId })
    }

    @Test
    fun getPopularMovies_ignoresItemsWithoutIdOrTitle() = runTest {
        val api = mock<CstvCatalogApiService>()
        (1..3).forEach { page ->
            whenever(api.popular("movie", page)).thenReturn(
                CatalogItemsResponseDto(
                    listOf(
                        CatalogItemDto(id = "movie:$page", kind = "movie", title = "Film $page", releaseYear = 2024),
                        CatalogItemDto(id = null, kind = "movie", title = "Sans id", releaseYear = 2024),
                        CatalogItemDto(id = "movie:x$page", kind = "movie", title = null, releaseYear = 2024)
                    )
                )
            )
        }

        val result = PopularRepositoryImpl(contextWithPrefs(), api, Gson()).getPopularMovies()

        assertEquals(listOf("movie:1", "movie:2", "movie:3"), result.map { it.canonicalId })
    }

    @Test
    fun getCachedMatchedMovies_skipsCacheOnFirstAccessThenServesIt() = runTest {
        val prefs = fakePrefs()
        whenever(prefs.getLong("movies_time_v2", 0L)).thenReturn(System.currentTimeMillis())
        whenever(prefs.getString("movies_data_v2", null)).thenReturn("""[{"localIds":[14,15]}]""")
        val repository = PopularRepositoryImpl(contextWithPrefs(prefs), mock(), Gson())

        // Premier accès du lancement : rafraîchissement forcé, le cache est ignoré.
        assertEquals(null, repository.getCachedMatchedMovies(lastVodCatalogSyncTime = 0L))
        verify(prefs, never()).getString("movies_data_v2", null)

        // Accès suivants : le cache reprend son rôle normal.
        val second = repository.getCachedMatchedMovies(lastVodCatalogSyncTime = 0L)
        assertEquals(listOf(14, 15), second?.single()?.localIds)
    }

    @Test
    fun getCachedMatchedMovies_readsCacheOnFirstAccess_whenSessionRefreshIgnored() = runTest {
        val prefs = fakePrefs()
        whenever(prefs.getLong("movies_time_v2", 0L)).thenReturn(System.currentTimeMillis())
        whenever(prefs.getString("movies_data_v2", null)).thenReturn("""[{"localIds":[14]}]""")
        val repository = PopularRepositoryImpl(contextWithPrefs(prefs), mock(), Gson())

        // Repli hors ligne : le cache doit rester lisible dès le premier accès.
        val result = repository.getCachedMatchedMovies(
            lastVodCatalogSyncTime = 0L,
            ignoreSessionRefresh = true
        )

        assertEquals(listOf(14), result?.single()?.localIds)
    }

    @Test
    fun getCachedMatchedSeries_skipsCacheOnFirstAccessThenServesIt() = runTest {
        val prefs = fakePrefs()
        whenever(prefs.getLong("series_time_v2", 0L)).thenReturn(System.currentTimeMillis())
        whenever(prefs.getString("series_data_v2", null)).thenReturn("""[{"localIds":[2]}]""")
        val repository = PopularRepositoryImpl(contextWithPrefs(prefs), mock(), Gson())

        assertEquals(null, repository.getCachedMatchedSeries(lastSeriesCatalogSyncTime = 0L))

        val second = repository.getCachedMatchedSeries(lastSeriesCatalogSyncTime = 0L)
        assertEquals(listOf(2), second?.single()?.localIds)
    }

    @Test
    fun getCachedMatchedMovies_andSeries_haveIndependentSessionGates() = runTest {
        val prefs = fakePrefs()
        whenever(prefs.getLong("series_time_v2", 0L)).thenReturn(System.currentTimeMillis())
        whenever(prefs.getString("series_data_v2", null)).thenReturn("""[{"localIds":[2]}]""")
        val repository = PopularRepositoryImpl(contextWithPrefs(prefs), mock(), Gson())

        // Consommer la porte Films ne doit pas consommer celle des Séries.
        repository.getCachedMatchedMovies(lastVodCatalogSyncTime = 0L)

        assertEquals(null, repository.getCachedMatchedSeries(lastSeriesCatalogSyncTime = 0L))
    }

    // --- isMoviesCacheExpired / isSeriesCacheExpired (T8-R1) ---

    @Test
    fun isMoviesCacheExpired_isFalseWithinTheNominalCacheDuration() = runTest {
        val prefs = fakePrefs()
        whenever(prefs.getLong("movies_time_v2", 0L)).thenReturn(System.currentTimeMillis())
        val repository = PopularRepositoryImpl(contextWithPrefs(prefs), mock(), Gson())

        assertFalse(repository.isMoviesCacheExpired(lastVodCatalogSyncTime = 0L))
    }

    @Test
    fun isMoviesCacheExpired_isTruePastTheNominalCacheDuration() = runTest {
        val prefs = fakePrefs()
        whenever(prefs.getLong("movies_time_v2", 0L)).thenReturn(System.currentTimeMillis() - 25 * 60 * 60 * 1000L)
        val repository = PopularRepositoryImpl(contextWithPrefs(prefs), mock(), Gson())

        assertTrue(repository.isMoviesCacheExpired(lastVodCatalogSyncTime = 0L))
    }

    @Test
    fun isSeriesCacheExpired_isTrueWhenNoCacheWasEverSaved() = runTest {
        val prefs = fakePrefs()
        whenever(prefs.getLong("series_time_v2", 0L)).thenReturn(0L)
        val repository = PopularRepositoryImpl(contextWithPrefs(prefs), mock(), Gson())

        assertTrue(repository.isSeriesCacheExpired(lastSeriesCatalogSyncTime = 0L))
    }

    @Test
    fun isSeriesCacheExpired_isTrueWhenTheCatalogWasResyncedAfterTheCacheWasSaved() = runTest {
        val prefs = fakePrefs()
        val savedAt = System.currentTimeMillis()
        whenever(prefs.getLong("series_time_v2", 0L)).thenReturn(savedAt)
        val repository = PopularRepositoryImpl(contextWithPrefs(prefs), mock(), Gson())

        assertTrue(repository.isSeriesCacheExpired(lastSeriesCatalogSyncTime = savedAt + 1_000L))
    }
}
