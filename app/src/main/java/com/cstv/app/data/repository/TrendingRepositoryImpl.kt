package com.cstv.app.data.repository

import com.cstv.app.data.remote.api.TmdbApiService
import com.cstv.app.domain.model.TrendingTitle
import com.cstv.app.domain.repository.TrendingRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrendingRepositoryImpl @Inject constructor(
    private val tmdbApiService: TmdbApiService,
    @com.cstv.app.di.TmdbApiKey private val apiKey: String
) : TrendingRepository {

    private var cachedTrending: List<TrendingTitle>? = null
    private var lastFetchTime: Long = 0
    private val cacheDurationMs = 3 * 60 * 60 * 1000 // 3 hours memory cache
    private val mutex = Mutex()

    override suspend fun getTrending(): List<TrendingTitle> {
        if (apiKey.isBlank()) {
            return emptyList()
        }

        val currentTime = System.currentTimeMillis()
        mutex.withLock {
            val cached = cachedTrending
            if (cached != null && (currentTime - lastFetchTime) < cacheDurationMs) {
                return cached
            }

            return try {
                val response = tmdbApiService.getTrending(apiKey)
                val items = response.results?.mapNotNull { item ->
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

                cachedTrending = items
                lastFetchTime = currentTime
                items
            } catch (e: Exception) {
                // Return empty list on network/parsing failure, never propagate to presentation
                emptyList()
            }
        }
    }
}
