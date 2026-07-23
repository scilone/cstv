package com.cstv.app.data.repository

import com.cstv.app.data.local.storage.CredentialsManager
import com.cstv.app.data.remote.api.TmdbApiService
import com.cstv.app.data.remote.api.XtreamApiService
import com.cstv.app.data.remote.api.XtreamRequestGate
import com.cstv.app.di.TmdbApiKey
import com.cstv.app.domain.model.TrailerMedia
import com.cstv.app.domain.model.TrailerPreview
import com.cstv.app.domain.model.TrailerSource
import com.cstv.app.domain.repository.TrailerRepository
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class TrailerRepositoryImpl @Inject constructor(
    private val xtreamApiService: XtreamApiService,
    private val tmdbApiService: TmdbApiService,
    private val credentialsManager: CredentialsManager,
    private val requestGate: XtreamRequestGate,
    @TmdbApiKey private val tmdbApiKey: String
) : TrailerRepository {

    private val cache = object : LinkedHashMap<TrailerMedia, TrailerPreview?>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<TrailerMedia, TrailerPreview?>) = size > 32
    }
    private val resolutionMutex = Mutex()

    override suspend fun getTrailerPreview(media: TrailerMedia): TrailerPreview? {
        synchronized(cache) { if (cache.containsKey(media)) return cache[media] }
        // Une seule résolution à la fois : la seconde demande du même média voit le
        // cache rempli par la première, et le panel Xtream reste protégé des rafales.
        return resolutionMutex.withLock {
            synchronized(cache) { if (cache.containsKey(media)) return@withLock cache[media] }
            val preview = try {
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
                normalizeYouTubeId(fromXtream)?.toPreview(media) ?: tmdbFallback(media)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                null
            }
            synchronized(cache) { cache[media] = preview }
            preview
        }
    }

    override fun clearSessionCache() = synchronized(cache) { cache.clear() }

    private suspend fun tmdbFallback(media: TrailerMedia): TrailerPreview? {
        if (tmdbApiKey.isBlank()) return null
        val videos = when (media) {
            is TrailerMedia.Movie -> tmdbApiService.getMovieVideos(media.tmdbId, tmdbApiKey).results
            is TrailerMedia.Series -> tmdbApiService.getSeriesVideos(media.tmdbId, tmdbApiKey).results
        }.orEmpty()
        val video = videos.firstOrNull { it.site.equals("YouTube", true) && it.type.equals("Trailer", true) && it.official == true }
            ?: videos.firstOrNull { it.site.equals("YouTube", true) && it.type.equals("Trailer", true) }
        return normalizeYouTubeId(video?.key)?.toPreview(media)
    }

    private fun String.toPreview(media: TrailerMedia) = TrailerPreview(media, TrailerSource.YouTube(this))

    companion object {
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
