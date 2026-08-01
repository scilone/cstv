package com.cstv.app.data.repository

import android.content.Context
import com.cstv.app.data.remote.api.TmdbApiService
import com.cstv.app.di.IptvLog
import com.cstv.app.di.TmdbApiKey
import com.cstv.app.domain.model.PopularCatalogItem
import com.cstv.app.domain.model.ReleaseYearParser
import com.cstv.app.domain.model.TrendingTitle
import com.cstv.app.domain.repository.PopularRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PopularRepositoryImpl @Inject constructor(
    private val context: Context,
    private val tmdbApiService: TmdbApiService,
    @TmdbApiKey private val apiKey: String,
    private val gson: Gson,
    private val sessionRefreshGate: TmdbSessionRefreshGate = TmdbSessionRefreshGate()
) : PopularRepository {

    private val sharedPrefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val mutex = Mutex()

    override suspend fun getPopularMovies(): List<TrendingTitle> =
        getPopular(isMovie = true)

    override suspend fun getPopularSeries(): List<TrendingTitle> =
        getPopular(isMovie = false)

    override suspend fun getCachedMatchedMovies(
        lastVodCatalogSyncTime: Long,
        ignoreSessionRefresh: Boolean
    ): List<PopularCatalogItem>? =
        getCached(MOVIES_DATA_KEY, MOVIES_TIME_KEY, lastVodCatalogSyncTime, ignoreSessionRefresh)

    override suspend fun getCachedMatchedSeries(
        lastSeriesCatalogSyncTime: Long,
        ignoreSessionRefresh: Boolean
    ): List<PopularCatalogItem>? =
        getCached(SERIES_DATA_KEY, SERIES_TIME_KEY, lastSeriesCatalogSyncTime, ignoreSessionRefresh)

    override suspend fun getCachedMatchedMoviesIgnoringAge(lastVodCatalogSyncTime: Long): List<PopularCatalogItem>? =
        getCached(MOVIES_DATA_KEY, MOVIES_TIME_KEY, lastVodCatalogSyncTime, ignoreSessionRefresh = true, ignoreAge = true)

    override suspend fun getCachedMatchedSeriesIgnoringAge(lastSeriesCatalogSyncTime: Long): List<PopularCatalogItem>? =
        getCached(SERIES_DATA_KEY, SERIES_TIME_KEY, lastSeriesCatalogSyncTime, ignoreSessionRefresh = true, ignoreAge = true)

    override suspend fun isMoviesCacheExpired(lastVodCatalogSyncTime: Long): Boolean =
        isCacheExpired(MOVIES_TIME_KEY, lastVodCatalogSyncTime)

    override suspend fun isSeriesCacheExpired(lastSeriesCatalogSyncTime: Long): Boolean =
        isCacheExpired(SERIES_TIME_KEY, lastSeriesCatalogSyncTime)

    override suspend fun saveMatchedMovies(items: List<PopularCatalogItem>) {
        save(MOVIES_DATA_KEY, MOVIES_TIME_KEY, items)
    }

    override suspend fun saveMatchedSeries(items: List<PopularCatalogItem>) {
        save(SERIES_DATA_KEY, SERIES_TIME_KEY, items)
    }

    private suspend fun getPopular(isMovie: Boolean): List<TrendingTitle> {
        if (apiKey.isBlank()) return emptyList()

        return try {
            val items = coroutineScope {
                (1..POPULAR_PAGE_COUNT).map { page ->
                    async {
                        if (isMovie) {
                            tmdbApiService.getPopularMovies(apiKey, page = page)
                        } else {
                            tmdbApiService.getPopularSeries(apiKey, page = page)
                        }
                    }
                }.flatMap { it.await().results.orEmpty() }
            }.take(POPULAR_CANDIDATE_LIMIT)
            items.mapNotNull { item ->
                val id = when (val rawId = item.id) {
                    is Number -> rawId.toInt()
                    is String -> rawId.toIntOrNull()
                    else -> null
                } ?: return@mapNotNull null
                val title = if (isMovie) item.title else item.name
                if (title.isNullOrBlank()) return@mapNotNull null
                val date = if (isMovie) item.releaseDate else item.firstAirDate
                TrendingTitle(
                    tmdbId = id,
                    title = title,
                    isMovie = isMovie,
                    year = ReleaseYearParser.parseYear(date),
                    posterUrl = item.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            IptvLog.e("TMDB", "Impossible de charger les populaires TMDB", e)
            emptyList()
        }
    }

    private suspend fun getCached(
        dataKey: String,
        timeKey: String,
        lastCatalogSyncTime: Long,
        ignoreSessionRefresh: Boolean,
        ignoreAge: Boolean = false
    ): List<PopularCatalogItem>? = mutex.withLock {
        if (!ignoreSessionRefresh && sessionRefreshGate.consumeFirstAccess(dataKey)) {
            IptvLog.d("TMDB", "💾 Cache Popular ($dataKey) ignoré au lancement : rafraîchissement forcé.")
            return null
        }
        val savedAt = sharedPrefs.getLong(timeKey, 0L)
        // savedAt < lastCatalogSyncTime reste vérifié même en ignorant l'âge :
        // un appariement plus vieux que le catalogue actuel référence des
        // streamId potentiellement disparus/réattribués, pas une simple
        // question de fraîcheur (T8 ne s'applique qu'à l'ancienneté nominale).
        if (savedAt == 0L || savedAt < lastCatalogSyncTime) return null
        if (!ignoreAge && System.currentTimeMillis() - savedAt >= CACHE_DURATION_MS) return null
        val json = sharedPrefs.getString(dataKey, null) ?: return null
        return try {
            gson.fromJson<List<PopularCatalogItem>>(
                json,
                object : TypeToken<List<PopularCatalogItem>>() {}.type
            ).takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            IptvLog.e("TMDB", "Cache Popular TMDB illisible", e)
            null
        }
    }

    /**
     * T8-R1 : même définition de fraîcheur que [getCached] (âge nominal +
     * cohérence avec le catalogue courant), mais sans lire/désérialiser le
     * contenu — seule la décision de rafraîchir en dépend.
     */
    private fun isCacheExpired(timeKey: String, lastCatalogSyncTime: Long): Boolean {
        val savedAt = sharedPrefs.getLong(timeKey, 0L)
        if (savedAt == 0L || savedAt < lastCatalogSyncTime) return true
        return System.currentTimeMillis() - savedAt >= CACHE_DURATION_MS
    }

    private suspend fun save(dataKey: String, timeKey: String, items: List<PopularCatalogItem>) {
        if (items.isEmpty()) return
        mutex.withLock {
            sharedPrefs.edit()
                .putString(dataKey, gson.toJson(items))
                .putLong(timeKey, System.currentTimeMillis())
                .apply()
        }
    }

    private companion object {
        const val PREFS_NAME = "tmdb_popular_cache"
        const val CACHE_DURATION_MS = 24 * 60 * 60 * 1000L
        const val POPULAR_PAGE_COUNT = 3
        const val POPULAR_CANDIDATE_LIMIT = 50
        const val MOVIES_DATA_KEY = "movies_data_v2"
        const val MOVIES_TIME_KEY = "movies_time_v2"
        const val SERIES_DATA_KEY = "series_data_v2"
        const val SERIES_TIME_KEY = "series_time_v2"
    }
}
