package com.cstv.app.data.repository

import com.cstv.app.data.local.storage.CredentialsManager
import com.cstv.app.data.local.dao.TrailerCacheDao
import com.cstv.app.data.local.entity.TrailerCacheEntity
import com.cstv.app.data.remote.api.TmdbApiService
import com.cstv.app.data.remote.api.XtreamApiService
import com.cstv.app.data.remote.api.XtreamRequestGate
import com.cstv.app.data.remote.dto.TmdbVideoDto
import com.cstv.app.data.remote.dto.TmdbVideosResponseDto
import com.cstv.app.data.remote.dto.VodInfoResponseDto
import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.model.TrailerMedia
import com.cstv.app.domain.model.TrailerSource
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import com.cstv.app.domain.util.TimeProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.never
import org.mockito.kotlin.verifyNoInteractions

class TrailerRepositoryImplTest {
    private val credentials = Credentials("https://panel.example", 443, "user", "password", true)
    private val xtream: XtreamApiService = mock()
    private val tmdb: TmdbApiService = mock()
    private val credentialsManager: CredentialsManager = mock()
    private val trailerCacheDao: TrailerCacheDao = mock()
    private val timeProvider = object : TimeProvider { override fun nowMillis() = 1_000L }
    private val repository = TrailerRepositoryImpl(
        xtream, tmdb, credentialsManager, XtreamRequestGate(), "tmdb-key", trailerCacheDao, timeProvider, CoroutineScope(Dispatchers.Unconfined)
    )

    @Test
    fun normalizeYouTubeId_acceptsOnlySupportedForms() {
        assertEquals("dQw4w9WgXcQ", TrailerRepositoryImpl.normalizeYouTubeId("dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", TrailerRepositoryImpl.normalizeYouTubeId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", TrailerRepositoryImpl.normalizeYouTubeId("https://youtu.be/dQw4w9WgXcQ"))
        assertNull(TrailerRepositoryImpl.normalizeYouTubeId("https://example.org/trailer"))
        assertNull(TrailerRepositoryImpl.normalizeYouTubeId("not a video id"))
    }

    @Test
    fun getTrailerPreview_usesFreshPersistentEntryWithoutNetworkCall() {
        runBlocking {
        val media = TrailerMedia.Movie(7, 42)
        whenever(trailerCacheDao.get("movie", 7)).thenReturn(
            TrailerCacheEntity("movie", 7, "dQw4w9WgXcQ", "youtube", 42, 999L)
        )

        val preview = repository.getTrailerPreview(media)

        assertEquals("dQw4w9WgXcQ", (preview?.source as TrailerSource.YouTube).videoId)
        verifyNoInteractions(xtream)
        }
    }

    // L'Accueil demande un média avec son tmdbId, la fiche de détails sans. Le
    // cache est indexé sur (type, id catalogue) : le preview rendu doit porter
    // le média demandé, sinon la fiche rejette le résultat de l'Accueil
    // (`preview.media == trailerMedia` faux) et n'affiche aucun trailer.
    @Test
    fun getTrailerPreview_returnsPreviewCarryingTheRequestedMedia() {
        runBlocking {
        doReturn(credentials).whenever(credentialsManager).getCredentials()
        val response: VodInfoResponseDto = mock()
        val info = mock<com.cstv.app.data.remote.dto.VodInfoDto>()
        doReturn(info).whenever(response).info
        doReturn("dQw4w9WgXcQ").whenever(info).youtubeTrailer
        whenever(xtream.getVodInfo("user", "password", 7)).thenReturn(response)

        val fromHome = repository.getTrailerPreview(TrailerMedia.Movie(7, 42))
        val fromDetails = repository.getTrailerPreview(TrailerMedia.Movie(7))

        assertEquals(TrailerMedia.Movie(7, 42), fromHome?.media)
        assertEquals(TrailerMedia.Movie(7), fromDetails?.media)
        assertEquals(TrailerSource.YouTube("dQw4w9WgXcQ"), fromDetails?.source)
        verify(xtream, times(1)).getVodInfo("user", "password", 7)
        }
    }

    @Test
    fun getTrailerPreview_doesNotPersistNetworkFailure() {
        runBlocking {
        doReturn(credentials).whenever(credentialsManager).getCredentials()
        whenever(xtream.getVodInfo("user", "password", 7)).thenThrow(IllegalStateException("offline"))

        assertNull(repository.getTrailerPreview(TrailerMedia.Movie(7, 42)))

        verify(trailerCacheDao, never()).upsert(org.mockito.kotlin.any())
        }
    }

    @Test
    fun getTrailerPreview_prefersXtreamAndCachesResult() {
        runBlocking {
        doReturn(credentials).whenever(credentialsManager).getCredentials()
        val response: VodInfoResponseDto = mock()
        val info = mock<com.cstv.app.data.remote.dto.VodInfoDto>()
        doReturn(info).whenever(response).info
        doReturn("dQw4w9WgXcQ").whenever(info).youtubeTrailer
        whenever(xtream.getVodInfo("user", "password", 7)).thenReturn(response)
        val media = TrailerMedia.Movie(7, 42)

        val first = repository.getTrailerPreview(media)
        val second = repository.getTrailerPreview(media)

        assertEquals(TrailerSource.YouTube("dQw4w9WgXcQ"), first?.source)
        assertEquals(first, second)
        verify(xtream, times(1)).getVodInfo("user", "password", 7)
        verify(tmdb, times(0)).getMovieVideos(42, "tmdb-key")
        Unit
        }
    }

    @Test
    fun getTrailerPreview_fallsBackToTmdbForMovieAndSeries() {
        runBlocking {
        doReturn(credentials).whenever(credentialsManager).getCredentials()
        val noTrailerMovie: VodInfoResponseDto = mock()
        doReturn(null).whenever(noTrailerMovie).info
        whenever(xtream.getVodInfo("user", "password", 1)).thenReturn(noTrailerMovie)
        val noTrailerSeries: com.cstv.app.data.remote.dto.SeriesInfoResponseDto = mock()
        doReturn(null).whenever(noTrailerSeries).info
        whenever(xtream.getSeriesInfo("user", "password", 2)).thenReturn(noTrailerSeries)
        val videos = TmdbVideosResponseDto(listOf(TmdbVideoDto("YouTube", "dQw4w9WgXcQ", "Trailer", true)))
        whenever(tmdb.getMovieVideos(10, "tmdb-key")).thenReturn(videos)
        whenever(tmdb.getSeriesVideos(20, "tmdb-key")).thenReturn(videos)

        assertEquals(TrailerSource.YouTube("dQw4w9WgXcQ"), repository.getTrailerPreview(TrailerMedia.Movie(1, 10))?.source)
        assertEquals(TrailerSource.YouTube("dQw4w9WgXcQ"), repository.getTrailerPreview(TrailerMedia.Series(2, 20))?.source)
        Unit
        }
    }

    @Test
    fun getTrailerPreview_rejectsInvalidSourceAndReturnsNullOnNetworkError() {
        runBlocking {
        doReturn(credentials).whenever(credentialsManager).getCredentials()
        val response: VodInfoResponseDto = mock()
        val info = mock<com.cstv.app.data.remote.dto.VodInfoDto>()
        doReturn(info).whenever(response).info
        doReturn("https://example.org/not-youtube").whenever(info).youtubeTrailer
        whenever(xtream.getVodInfo("user", "password", 8)).thenReturn(response)
        whenever(tmdb.getMovieVideos(43, "tmdb-key")).thenThrow(IllegalStateException("offline"))

        assertNull(repository.getTrailerPreview(TrailerMedia.Movie(8, 43)))
        Unit
        }
    }

    @Test
    fun clearSessionCache_forcesFreshResolutionIncludingNegativeEntries() {
        runBlocking {
        doReturn(credentials).whenever(credentialsManager).getCredentials()
        val response: VodInfoResponseDto = mock()
        doReturn(null).whenever(response).info
        whenever(xtream.getVodInfo("user", "password", 9)).thenReturn(response)
        whenever(tmdb.getMovieVideos(44, "tmdb-key")).thenReturn(TmdbVideosResponseDto(emptyList()))
        val media = TrailerMedia.Movie(9, 44)

        assertNull(repository.getTrailerPreview(media))
        assertNull(repository.getTrailerPreview(media))
        verify(xtream, times(1)).getVodInfo("user", "password", 9)
        repository.clearSessionCache()
        assertNull(repository.getTrailerPreview(media))
        verify(xtream, times(2)).getVodInfo("user", "password", 9)
        Unit
        }
    }
}
