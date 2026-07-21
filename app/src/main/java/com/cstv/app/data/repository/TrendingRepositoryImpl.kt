package com.cstv.app.data.repository

import android.content.Context
import com.cstv.app.data.remote.api.TmdbApiService
import com.cstv.app.domain.model.TrendingCatalogItem
import com.cstv.app.domain.model.TrendingTitle
import com.cstv.app.domain.model.ReleaseYearParser
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
    private val tmdbApiService: TmdbApiService,
    @com.cstv.app.di.TmdbApiKey private val apiKey: String,
    private val gson: Gson
) : TrendingRepository {

    private val prefsName = "tmdb_trends_cache"
    private val sharedPrefs by lazy { context.getSharedPreferences(prefsName, Context.MODE_PRIVATE) }
    private val cacheDurationMs = 24 * 60 * 60 * 1000L // 24 hours persistent global cache
    private val mutex = Mutex()

    override suspend fun getTrending(): List<TrendingTitle> {
        if (apiKey.isBlank()) {
            com.cstv.app.di.IptvLog.e("TMDB", "❌ TMDB API Key is blank! Fallback to standard hero.")
            return emptyList()
        }

        val maskedKey = if (apiKey.length > 5) apiKey.take(5) + "..." else "invalid"
        com.cstv.app.di.IptvLog.d("TMDB", "🌐 Fetching trending items from TMDB with key prefix: $maskedKey")

        return try {
            val response = tmdbApiService.getTrending(apiKey)
            val results = response.results
            if (results == null) {
                com.cstv.app.di.IptvLog.w("TMDB", "⚠️ TMDB response results are null!")
            } else {
                com.cstv.app.di.IptvLog.d("TMDB", "✅ TMDB returned ${results.size} trending items.")
            }

            results?.mapNotNull { item ->
                val id = when (val rawId = item.id) {
                    is Number -> rawId.toInt()
                    is String -> rawId.toIntOrNull()
                    else -> null
                } ?: return@mapNotNull null

                val title = item.title ?: item.name ?: return@mapNotNull null
                val isMovie = item.mediaType == "movie"
                
                val fullDate = if (isMovie) item.releaseDate else item.firstAirDate
                val year = ReleaseYearParser.parseYear(fullDate)

                val posterUrl = item.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }

                TrendingTitle(
                    tmdbId = id,
                    title = title,
                    isMovie = isMovie,
                    year = year,
                    posterUrl = posterUrl
                )
            } ?: emptyList()
        } catch (e: Exception) {
            com.cstv.app.di.IptvLog.e("TMDB", "❌ Exception while fetching trends from TMDB", e)
            emptyList()
        }
    }

    override suspend fun getCachedMatchedTrendsGlobal(lastCatalogSyncTime: Long): List<TrendingCatalogItem>? = mutex.withLock {
        val lastFetchTime = sharedPrefs.getLong("trends_time_global_v3", 0L)
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastFetchTime >= cacheDurationMs) {
            com.cstv.app.di.IptvLog.d("TMDB", "💾 Global trends cache expired.")
            return null // Cache expired
        }

        if (lastFetchTime < lastCatalogSyncTime) {
            com.cstv.app.di.IptvLog.d("TMDB", "💾 Global trends cache invalidated because the catalog was resynchronized.")
            return null // Invalidate cache on catalog update
        }

        val json = sharedPrefs.getString("trends_data_global_v3", null) ?: run {
            com.cstv.app.di.IptvLog.d("TMDB", "💾 Global trends cache is empty.")
            return null
        }
        return try {
            val type = object : TypeToken<List<TrendingCatalogItem>>() {}.type
            val list = gson.fromJson<List<TrendingCatalogItem>>(json, type)
            com.cstv.app.di.IptvLog.d("TMDB", "💾 Global trends cache hit! Loaded ${list.size} matched items.")
            list
        } catch (e: Exception) {
            com.cstv.app.di.IptvLog.e("TMDB", "💾 Exception while parsing global trends cache", e)
            null // Fallback to re-fetch on parsing failure
        }
    }

    override suspend fun saveMatchedTrendsGlobal(items: List<TrendingCatalogItem>) {
        mutex.withLock {
            try {
                val json = gson.toJson(items)
                sharedPrefs.edit()
                    .putString("trends_data_global_v3", json)
                    .putLong("trends_time_global_v3", System.currentTimeMillis())
                    .apply()
                com.cstv.app.di.IptvLog.d("TMDB", "💾 Global matched trends successfully saved in persistent cache.")
            } catch (e: Exception) {
                com.cstv.app.di.IptvLog.e("TMDB", "💾 Failed to save matched trends to persistent cache", e)
            }
        }
    }
}
