package com.cstv.app.data.repository

import android.content.Context
import com.cstv.app.data.remote.api.TmdbApiService
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
            return emptyList()
        }

        return try {
            val response = tmdbApiService.getTrending(apiKey)
            response.results?.mapNotNull { item ->
                val id = when (val rawId = item.id) {
                    is Number -> rawId.toInt()
                    is String -> rawId.toIntOrNull()
                    else -> null
                } ?: return@mapNotNull null

                val title = item.title ?: item.name ?: return@mapNotNull null
                val isMovie = item.mediaType == "movie"
                
                val fullDate = if (isMovie) item.releaseDate else item.firstAirDate
                val year = fullDate?.take(4) // Extract year only (e.g. "2026")

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
            emptyList()
        }
    }

    override suspend fun getCachedMatchedTrendsGlobal(): List<TrendingCatalogItem>? = mutex.withLock {
        val lastFetchTime = sharedPrefs.getLong("trends_time_global", 0L)
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastFetchTime >= cacheDurationMs) {
            return null // Cache expired
        }

        val json = sharedPrefs.getString("trends_data_global", null) ?: return null
        return try {
            val type = object : TypeToken<List<TrendingCatalogItem>>() {}.type
            gson.fromJson<List<TrendingCatalogItem>>(json, type)
        } catch (e: Exception) {
            null // Fallback to re-fetch on parsing failure
        }
    }

    override suspend fun saveMatchedTrendsGlobal(items: List<TrendingCatalogItem>) {
        mutex.withLock {
            try {
                val json = gson.toJson(items)
                sharedPrefs.edit()
                    .putString("trends_data_global", json)
                    .putLong("trends_time_global", System.currentTimeMillis())
                    .apply()
            } catch (e: Exception) {
                // Ignore save failures
            }
        }
    }
}
