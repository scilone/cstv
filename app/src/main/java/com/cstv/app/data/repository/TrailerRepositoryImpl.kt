package com.cstv.app.data.repository

import com.cstv.app.data.local.storage.CredentialsManager
import com.cstv.app.data.remote.api.TmdbApiService
import com.cstv.app.data.remote.api.XtreamApiService
import com.cstv.app.data.remote.api.XtreamRequestGate
import com.cstv.app.di.TmdbApiKey
import com.cstv.app.domain.model.TrailerMedia
import com.cstv.app.domain.model.TrailerPreview
import com.cstv.app.domain.model.TrailerSource
import com.cstv.app.domain.model.TrailerLookupMatcher
import com.cstv.app.domain.repository.TrailerRepository
import com.cstv.app.data.local.dao.TrailerCacheDao
import com.cstv.app.data.local.entity.TrailerCacheEntity
import com.cstv.app.domain.model.TitleNormalizer
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import javax.inject.Named
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.cstv.app.domain.util.TimeProvider

@Singleton
class TrailerRepositoryImpl @Inject constructor(
    private val xtreamApiService: XtreamApiService,
    private val tmdbApiService: TmdbApiService,
    private val credentialsManager: CredentialsManager,
    private val requestGate: XtreamRequestGate,
    @TmdbApiKey private val tmdbApiKey: String,
    private val trailerCacheDao: TrailerCacheDao,
    private val timeProvider: TimeProvider,
    @Named("applicationScope") private val applicationScope: CoroutineScope
) : TrailerRepository {

    private data class CacheKey(val mediaType: String, val catalogId: Int)
    private data class Resolution(val preview: TrailerPreview?, val tmdbId: Int?, val cacheable: Boolean)
    private val cache = object : LinkedHashMap<CacheKey, TrailerPreview?>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, TrailerPreview?>) = size > 32
    }
    private val resolutionMutex = Mutex()

    override suspend fun getTrailerPreview(media: TrailerMedia): TrailerPreview? {
        val key = media.cacheKey()
        synchronized(cache) { if (cache.containsKey(key)) return cache[key] }
        // Une seule résolution à la fois : la seconde demande du même média voit le
        // cache rempli par la première, et le panel Xtream reste protégé des rafales.
        return resolutionMutex.withLock {
            synchronized(cache) { if (cache.containsKey(key)) return@withLock cache[key] }
            val stored = runCatching { trailerCacheDao.get(key.mediaType, key.catalogId) }.getOrNull()
            if (stored != null) {
                val ttl = if (stored.videoId == null) NEGATIVE_TTL_MS else POSITIVE_TTL_MS
                if (timeProvider.nowMillis() - stored.resolvedAt < ttl) {
                    return@withLock stored.videoId?.toPreview(media).also { synchronized(cache) { cache[key] = it } }
                }
                runCatching { trailerCacheDao.delete(key.mediaType, key.catalogId) }
            }
            val resolution = try {
                val fromXtream = credentialsManager.getCredentials()?.let { credentials ->
                    when (media) {
                        is TrailerMedia.Movie -> requestGate.acquire {
                            xtreamApiService.getVodInfo(credentials.username, credentials.password, media.catalogId)
                        }.info?.youtubeTrailer
                        is TrailerMedia.Series -> requestGate.acquire {
                            xtreamApiService.getSeriesInfo(credentials.username, credentials.password, media.catalogId)
                        }.info?.youtubeTrailer
                    }
                }
                normalizeYouTubeId(fromXtream)?.let { Resolution(it.toPreview(media), media.tmdbId, true) }
                    ?: tmdbFallback(media, stored?.resolvedTmdbId)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Resolution(null, null, false)
            }
            synchronized(cache) { cache[key] = resolution.preview }
            if (resolution.cacheable) runCatching {
                trailerCacheDao.upsert(TrailerCacheEntity(key.mediaType, key.catalogId, (resolution.preview?.source as? TrailerSource.YouTube)?.videoId, "youtube", resolution.tmdbId, timeProvider.nowMillis()))
            }
            resolution.preview
        }
    }

    override fun clearSessionCache() {
        synchronized(cache) { cache.clear() }
        applicationScope.launch { runCatching { trailerCacheDao.clear() } }
    }

    override suspend fun invalidate(media: TrailerMedia) {
        synchronized(cache) { cache.remove(media.cacheKey()) }
        runCatching { trailerCacheDao.delete(media.cacheKey().mediaType, media.catalogId) }
    }

    private suspend fun tmdbFallback(media: TrailerMedia, cachedTmdbId: Int? = null): Resolution {
        if (tmdbApiKey.isBlank()) return Resolution(null, null, false)
        val tmdbId = media.tmdbId ?: cachedTmdbId ?: resolveTmdbId(media) ?: return Resolution(null, null, true)
        val videos = when (media) {
            is TrailerMedia.Movie -> tmdbApiService.getMovieVideos(tmdbId, tmdbApiKey).results
            is TrailerMedia.Series -> tmdbApiService.getSeriesVideos(tmdbId, tmdbApiKey).results
        }.orEmpty()
        val video = videos.firstOrNull { it.site.equals("YouTube", true) && it.type.equals("Trailer", true) && it.official == true }
            ?: videos.firstOrNull { it.site.equals("YouTube", true) && it.type.equals("Trailer", true) }
        return Resolution(normalizeYouTubeId(video?.key)?.toPreview(media), tmdbId, true)
    }

    private suspend fun resolveTmdbId(media: TrailerMedia): Int? {
        val title = media.title ?: return null
        if (tmdbApiKey.isBlank()) return null
        val results = when (media) {
            is TrailerMedia.Movie -> tmdbApiService.searchMovies(TitleNormalizer.normalize(title), tmdbApiKey, media.releaseYear).results
            is TrailerMedia.Series -> tmdbApiService.searchSeries(TitleNormalizer.normalize(title), tmdbApiKey, media.releaseYear).results
        }.orEmpty()
        return TrailerLookupMatcher.select(title, media.releaseYear, results.mapNotNull { item ->
            val id = item.id?.toString()?.toIntOrNull() ?: return@mapNotNull null
            TrailerLookupMatcher.Candidate(id, item.title ?: item.name, (item.releaseDate ?: item.firstAirDate)?.take(4)?.toIntOrNull())
        })?.id
    }

    private fun String.toPreview(media: TrailerMedia) = TrailerPreview(media, TrailerSource.YouTube(this))

    private fun TrailerMedia.cacheKey() = CacheKey(if (this is TrailerMedia.Movie) "movie" else "series", catalogId)

    companion object {
        private const val POSITIVE_TTL_MS = 30L * 24 * 60 * 60 * 1000
        private const val NEGATIVE_TTL_MS = 7L * 24 * 60 * 60 * 1000
        /** Accepte uniquement un ID YouTube ou les URLs youtube.com/youtu.be reconnues. */
        internal fun normalizeYouTubeId(value: String?): String? {
            val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (raw.matches(Regex("[A-Za-z0-9_-]{11}"))) return raw
            val uri = runCatching { URI(raw) }.getOrNull() ?: return null
            val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
            val candidate = when (host) {
                "youtu.be" -> uri.path.trim('/').substringBefore('/').substringBefore('?')
                "youtube.com", "m.youtube.com" -> when {
                    uri.path == "/watch" -> uri.query?.split('&')?.firstOrNull { it.startsWith("v=") }?.removePrefix("v=")
                    uri.path.startsWith("/embed/") -> uri.path.removePrefix("/embed/").substringBefore('/')
                    uri.path.startsWith("/shorts/") -> uri.path.removePrefix("/shorts/").substringBefore('/')
                    else -> null
                }
                else -> null
            }
            return candidate?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
        }
    }
}
