package com.cstv.app.data.repository

import android.content.Context
import com.cstv.app.data.remote.api.CstvCatalogApiService
import com.cstv.app.domain.model.TrendingCatalogItem
import com.cstv.app.domain.model.TrendingTitle
import com.cstv.app.domain.repository.TrendingRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrendingRepositoryImpl @Inject constructor(
    private val context: Context,
    private val catalogApiService: CstvCatalogApiService,
    private val gson: Gson,
    private val sessionRefreshGate: TmdbSessionRefreshGate = TmdbSessionRefreshGate()
) : TrendingRepository {

    private val prefsName = "catalog_trends_cache"
    private val sharedPrefs by lazy { context.getSharedPreferences(prefsName, Context.MODE_PRIVATE) }
    private val cacheDurationMs = 4 * 60 * 60 * 1000L
    private val mutex = Mutex()

    // Purge paresseuse : ne touche la préférence legacy qu'au premier accès
    // réel au cache, jamais à la construction (un Context de test non stubbé
    // pour ce nom de préférence ne doit pas faire planter l'injection).
    private val legacyPrefsCleared by lazy {
        // Les caches directs TMDB ne doivent pas survivre au basculement CSTV.
        context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        true
    }

    override suspend fun getTrending(): List<TrendingTitle> {
        legacyPrefsCleared
        return try {
            val response = catalogApiService.trending()
            val results = response.items
            if (results == null) {
                com.cstv.app.di.IptvLog.w("Catalog", "Catalog response items are null.")
            } else {
                com.cstv.app.di.IptvLog.d("Catalog", "Catalog returned ${results.size} trending items.")
            }

            results?.mapNotNull { item ->
                val canonicalId = item.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val title = item.title ?: return@mapNotNull null
                val isMovie = item.kind == "movie"

                TrendingTitle(
                    canonicalId = canonicalId,
                    title = title,
                    isMovie = isMovie,
                    year = item.releaseYear,
                    posterUrl = item.posterUrl,
                    backdropUrl = item.backdropUrl
                )
            } ?: emptyList()
        } catch (e: Exception) {
            com.cstv.app.di.IptvLog.w("Catalog", "Catalog trends unavailable: ${e.javaClass.simpleName}")
            emptyList()
        }
    }

    override suspend fun getCachedMatchedTrendsGlobal(
        lastCatalogSyncTime: Long,
        ignoreSessionRefresh: Boolean,
        ignoreExpiration: Boolean,
        ignoreCatalogSync: Boolean
    ): List<TrendingCatalogItem>? = mutex.withLock {
        legacyPrefsCleared
        if (!ignoreSessionRefresh && sessionRefreshGate.consumeFirstAccess(TRENDS_DATA_KEY)) {
            com.cstv.app.di.IptvLog.d("Catalog", "Cache tendances ignoré au lancement : rafraîchissement forcé.")
            return null
        }
        val lastFetchTime = sharedPrefs.getLong("trends_time_global_v3", 0L)
        val currentTime = System.currentTimeMillis()

        val ageMs = currentTime - lastFetchTime
        if ((!ignoreExpiration && ageMs >= cacheDurationMs) || (ignoreExpiration && ageMs >= MAX_STALE_CACHE_MS)) {
            com.cstv.app.di.IptvLog.d("Catalog", "Global trends cache expired.")
            return null // Cache expired
        }

        if (!ignoreCatalogSync && lastFetchTime < lastCatalogSyncTime) {
            com.cstv.app.di.IptvLog.d("Catalog", "Global trends cache invalidated because the catalog was resynchronized.")
            return null // Invalidate cache on catalog update
        }

        val json = sharedPrefs.getString(TRENDS_DATA_KEY, null) ?: run {
            com.cstv.app.di.IptvLog.d("Catalog", "Global trends cache is empty.")
            return null
        }
        return try {
            val type = object : TypeToken<List<TrendingCatalogItem>>() {}.type
            val list = gson.fromJson<List<TrendingCatalogItem>>(json, type)
            com.cstv.app.di.IptvLog.d("Catalog", "Global trends cache hit: ${list.size} matched items.")
            list
        } catch (e: Exception) {
            com.cstv.app.di.IptvLog.e("Catalog", "Exception while parsing global trends cache", e)
            null // Fallback to re-fetch on parsing failure
        }
    }

    override suspend fun isCacheExpired(lastCatalogSyncTime: Long): Boolean = mutex.withLock {
        val lastFetchTime = sharedPrefs.getLong("trends_time_global_v3", 0L)
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastFetchTime >= cacheDurationMs) {
            return true
        }
        if (lastFetchTime < lastCatalogSyncTime) {
            return true
        }
        return false
    }

    override suspend fun saveMatchedTrendsGlobal(items: List<TrendingCatalogItem>) {
        mutex.withLock {
            try {
                val json = gson.toJson(items)
                sharedPrefs.edit()
                    .putString(TRENDS_DATA_KEY, json)
                    .putLong("trends_time_global_v3", System.currentTimeMillis())
                    .apply()
                com.cstv.app.di.IptvLog.d("Catalog", "Global matched trends saved in persistent cache.")
            } catch (e: Exception) {
                com.cstv.app.di.IptvLog.e("Catalog", "Failed to save matched trends to persistent cache", e)
            }
        }
    }

    private companion object {
        const val TRENDS_DATA_KEY = "trends_data_global_v3"
        const val MAX_STALE_CACHE_MS = 24 * 60 * 60 * 1000L
        const val LEGACY_PREFS_NAME = "tmdb_trends_cache"
    }
}
