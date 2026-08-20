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

class TrendingRepositoryImplTest {
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
        whenever(context.getSharedPreferences("catalog_trends_cache", Context.MODE_PRIVATE)).thenReturn(prefs)
        whenever(context.getSharedPreferences("tmdb_trends_cache", Context.MODE_PRIVATE)).thenReturn(legacyPrefs)
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
    fun getTrending_mapsProductContract() = runTest {
        val api = mock<CstvCatalogApiService>()
        whenever(api.trending()).thenReturn(CatalogItemsResponseDto(listOf(CatalogItemDto(externalId = "00000000-0000-4000-8000-000000000101", id = "movie:101", kind = "movie", title = "Inception", releaseYear = 2010, posterUrl = "https://cdn.example/p.jpg", backdropUrl = "https://cdn.example/b.jpg"))))
        val result = TrendingRepositoryImpl(contextWithPrefs(), api, Gson()).getTrending()
        assertEquals("00000000-0000-4000-8000-000000000101", result.single().externalId); assertEquals("Inception", result.single().title); assertEquals("https://cdn.example/b.jpg", result.single().backdropUrl)
    }

    @Test
    fun getTrending_silentlyDegradesOnCatalogFailure() = runTest {
        val api = mock<CstvCatalogApiService>(); whenever(api.trending()).thenThrow(IllegalStateException("offline"))
        assertTrue(TrendingRepositoryImpl(contextWithPrefs(), api, Gson()).getTrending().isEmpty())
    }

    @Test
    fun getCachedMatchedTrendsGlobal_skipsCacheOnFirstAccessThenServesIt() = runTest {
        val prefs = fakePrefs()
        val json = """[{"trendingTitle":{"externalId":"movie:1","title":"Dune","isMovie":true,"year":2021}}]"""
        whenever(prefs.getLong("trends_time_global_v3", 0L)).thenReturn(System.currentTimeMillis())
        whenever(prefs.getString("trends_data_global_v3", null)).thenReturn(json)
        val repository = TrendingRepositoryImpl(contextWithPrefs(prefs), mock(), Gson())

        // Premier accès du lancement : rafraîchissement forcé, le cache est ignoré.
        assertEquals(null, repository.getCachedMatchedTrendsGlobal(lastCatalogSyncTime = 0L))
        verify(prefs, never()).getString("trends_data_global_v3", null)

        // Accès suivants : le cache reprend son rôle normal.
        val second = repository.getCachedMatchedTrendsGlobal(lastCatalogSyncTime = 0L)
        assertEquals(1, second?.size)
    }

    @Test
    fun getCachedMatchedTrendsGlobal_readsCacheOnFirstAccess_whenSessionRefreshIgnored() = runTest {
        val prefs = fakePrefs()
        val json = """[{"trendingTitle":{"externalId":"movie:1","title":"Dune","isMovie":true,"year":2021}}]"""
        whenever(prefs.getLong("trends_time_global_v3", 0L)).thenReturn(System.currentTimeMillis())
        whenever(prefs.getString("trends_data_global_v3", null)).thenReturn(json)
        val repository = TrendingRepositoryImpl(contextWithPrefs(prefs), mock(), Gson())

        // Repli hors ligne : le cache doit rester lisible dès le premier accès.
        val result = repository.getCachedMatchedTrendsGlobal(
            lastCatalogSyncTime = 0L,
            ignoreSessionRefresh = true
        )

        assertEquals(1, result?.size)
    }

    @Test
    fun getCachedMatchedTrendsGlobal_servesExpiredCache_whenIgnoreExpirationIsTrue() = runTest {
        val prefs = fakePrefs()
        val json = """[{"trendingTitle":{"externalId":"movie:1","title":"Dune","isMovie":true,"year":2021}}]"""
        // 10h dans le passé : périmé pour le TTL frais local (4h), mais dans la
        // fenêtre de repli hors ligne (24h, MAX_STALE_CACHE_MS).
        val expiredTime = System.currentTimeMillis() - (10 * 60 * 60 * 1000L)
        whenever(prefs.getLong("trends_time_global_v3", 0L)).thenReturn(expiredTime)
        whenever(prefs.getString("trends_data_global_v3", null)).thenReturn(json)
        val repository = TrendingRepositoryImpl(contextWithPrefs(prefs), mock(), Gson())

        // Sans ignoreExpiration : le cache périmé n'est pas servi.
        val resultNull = repository.getCachedMatchedTrendsGlobal(
            lastCatalogSyncTime = 0L,
            ignoreSessionRefresh = true,
            ignoreExpiration = false
        )
        assertEquals(null, resultNull)

        // Avec ignoreExpiration, dans la fenêtre de repli de 24h : servi quand même.
        val resultCached = repository.getCachedMatchedTrendsGlobal(
            lastCatalogSyncTime = 0L,
            ignoreSessionRefresh = true,
            ignoreExpiration = true
        )
        assertEquals(1, resultCached?.size)
    }

    @Test
    fun getCachedMatchedTrendsGlobal_invalidatedByCatalogResync() = runTest {
        val prefs = fakePrefs()
        val json = """[{"trendingTitle":{"externalId":"movie:1","title":"Dune","isMovie":true,"year":2021}}]"""
        val savedAt = System.currentTimeMillis()
        whenever(prefs.getLong("trends_time_global_v3", 0L)).thenReturn(savedAt)
        whenever(prefs.getString("trends_data_global_v3", null)).thenReturn(json)
        val repository = TrendingRepositoryImpl(contextWithPrefs(prefs), mock(), Gson())

        // Le catalogue local a été resynchronisé après l'enregistrement du cache.
        val result = repository.getCachedMatchedTrendsGlobal(
            lastCatalogSyncTime = savedAt + 1_000L,
            ignoreSessionRefresh = true
        )

        assertEquals(null, result)
    }

    @Test
    fun isCacheExpired_returnsCorrectValidity() = runTest {
        val prefs = fakePrefs()
        val repository = TrendingRepositoryImpl(contextWithPrefs(prefs), mock(), Gson())

        // 1. Cache valide.
        whenever(prefs.getLong("trends_time_global_v3", 0L)).thenReturn(System.currentTimeMillis())
        assertFalse(repository.isCacheExpired(lastCatalogSyncTime = 0L))

        // 2. Cache périmé (5h, au-delà du TTL local de 4h).
        whenever(prefs.getLong("trends_time_global_v3", 0L)).thenReturn(System.currentTimeMillis() - (5 * 60 * 60 * 1000L))
        assertTrue(repository.isCacheExpired(lastCatalogSyncTime = 0L))

        // 3. Cache antérieur à la dernière resynchronisation du catalogue.
        val catalogSyncTime = System.currentTimeMillis()
        val cacheTime = catalogSyncTime - 1000L
        whenever(prefs.getLong("trends_time_global_v3", 0L)).thenReturn(cacheTime)
        assertTrue(repository.isCacheExpired(lastCatalogSyncTime = catalogSyncTime))
    }
}
