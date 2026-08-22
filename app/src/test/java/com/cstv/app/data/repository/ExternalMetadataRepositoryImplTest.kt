package com.cstv.app.data.repository

import com.cstv.app.data.local.dao.ExternalMetadataDao
import com.cstv.app.data.local.dao.LocalMatchProjection
import com.cstv.app.data.local.entity.ExternalMediaEntity
import com.cstv.app.data.local.entity.ExternalMediaLinkEntity
import com.cstv.app.data.local.entity.ExternalMovieEntity
import com.cstv.app.data.local.entity.ExternalSeriesEntity
import com.cstv.app.data.remote.api.CstvCatalogApiService
import com.cstv.app.data.remote.api.DeviceTypeProvider
import com.cstv.app.data.remote.dto.CatalogCacheDto
import com.cstv.app.data.remote.dto.CatalogMatchBatchResponseDto
import com.cstv.app.data.remote.dto.CatalogItemDto
import com.cstv.app.data.remote.dto.CatalogMatchQualityDto
import com.cstv.app.data.remote.dto.CatalogMatchRequestDto
import com.cstv.app.data.remote.dto.CatalogMatchResponseDto
import com.cstv.app.data.remote.dto.CatalogSeasonDto
import com.cstv.app.data.remote.dto.CatalogEpisodeDto
import com.cstv.app.domain.model.CatalogThrottledException
import com.cstv.app.domain.model.ExternalMatchHints
import com.cstv.app.domain.model.ExternalMetadataMatchOutcome
import com.cstv.app.domain.model.ExternalMetadataMatchRequest
import com.cstv.app.domain.model.matchOrNull
import com.cstv.app.domain.util.TimeProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import retrofit2.HttpException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class ExternalMetadataRepositoryImplTest {
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)

    private val api: CstvCatalogApiService = mock()
    private val dao: ExternalMetadataDao = mock()
    private val deviceType: DeviceTypeProvider = mock()
    private val timeProvider = object : TimeProvider { override fun nowMillis() = 1_000L }
    private val repository = ExternalMetadataRepositoryImpl(api, dao, deviceType, timeProvider)

    init {
        whenever(deviceType.current()).thenReturn("tv")
    }

    private fun localMatch(
        externalId: String = "5e37ba2a-1cda-4faf-9f10-335b2f6556a7",
        confidence: Int? = 90,
        matchMethod: String? = "title+year",
        matchVersion: Int? = 1,
        ageRating: Int? = 13,
        refreshAfter: Long? = 10_000L,
    ) = LocalMatchProjection(externalId, confidence, matchMethod, matchVersion, ageRating, refreshAfter)

    @Test
    fun `an already-linked provider id is resolved locally without a network call`() = runBlocking<Unit> {
        whenever(dao.findLocalMatch("movie", 42)).thenReturn(localMatch())

        val result = repository.match("movie", 42, "Dune", 2021, null)

        assertEquals("5e37ba2a-1cda-4faf-9f10-335b2f6556a7", result.matchOrNull?.externalId)
        verify(api, never()).match(any(), any())
    }

    @Test
    fun `F45-R4 a local hit restores confidence, method, version and ageRating from Room, not hardcoded nulls`() = runBlocking<Unit> {
        // Avant le fix, ce chemin renvoyait systématiquement confidence/method/version/ageRating à
        // null même quand `external_movies`/`external_series` avait la vraie valeur — un média
        // déjà classifié redevenait UNCLASSIFIED pour F44 dès l'expiration du cache mémoire.
        whenever(dao.findLocalMatch("movie", 42)).thenReturn(localMatch(confidence = 96, matchMethod = "title+year+director", matchVersion = 2, ageRating = 17))

        val result = repository.match("movie", 42, "Dune", 2021, null)

        assertEquals(96, result.matchOrNull?.confidence)
        assertEquals("title+year+director", result.matchOrNull?.matchMethod)
        assertEquals(2, result.matchOrNull?.matchVersion)
        assertEquals(17, result.matchOrNull?.ageRating)
        assertEquals(false, result.matchOrNull?.fromNetwork)
    }

    @Test
    fun `F45-R5 a fresh local hit is served as-is even when allowRefresh is true`() = runBlocking<Unit> {
        whenever(dao.findLocalMatch("movie", 42)).thenReturn(localMatch(refreshAfter = 50_000L)) // bien après now=1_000L

        val result = repository.match("movie", 42, "Dune", 2021, null, allowRefresh = true)

        assertEquals("5e37ba2a-1cda-4faf-9f10-335b2f6556a7", result.matchOrNull?.externalId)
        verify(api, never()).match(any(), any())
    }

    @Test
    fun `F45-R5 a stale local hit is never refreshed when allowRefresh is false (background priorities)`() = runBlocking<Unit> {
        whenever(dao.findLocalMatch("movie", 42)).thenReturn(localMatch(refreshAfter = 1L)) // largement dépassé (now=1_000L)

        val result = repository.match("movie", 42, "Dune", 2021, null, allowRefresh = false)

        assertEquals("5e37ba2a-1cda-4faf-9f10-335b2f6556a7", result.matchOrNull?.externalId)
        verify(api, never()).match(any(), any())
    }

    @Test
    fun `F45-R5 a stale local hit falls through to the network only when allowRefresh is true (DETAIL_OPEN)`() = runBlocking<Unit> {
        whenever(dao.findLocalMatch("movie", 42)).thenReturn(localMatch(refreshAfter = 1L)) // dépassé
        whenever(api.match(any(), eq("tv"))).thenReturn(
            CatalogMatchResponseDto(
                status = "matched",
                match = CatalogMatchQualityDto(confidence = 96, method = "title+year", version = 1),
                item = CatalogItemDto(externalId = "5e37ba2a-1cda-4faf-9f10-335b2f6556a7", kind = "movie", title = "Dune", ageRating = 13),
            ),
        )

        val result = repository.match("movie", 42, "Dune", 2021, null, allowRefresh = true)

        verify(api).match(any(), eq("tv"))
        assertEquals(true, result.matchOrNull?.fromNetwork)
    }

    @Test
    fun `F45-R5 refreshAfter with no null refreshAfter is treated as stale (self-heals pre-existing rows)`() = runBlocking<Unit> {
        whenever(dao.findLocalMatch("movie", 42)).thenReturn(localMatch(refreshAfter = null))
        whenever(api.match(any(), eq("tv"))).thenReturn(
            CatalogMatchResponseDto(status = "matched", match = CatalogMatchQualityDto(confidence = 90, method = "title", version = 1), item = CatalogItemDto(externalId = "5e37ba2a-1cda-4faf-9f10-335b2f6556a7", kind = "movie", title = "Dune")),
        )

        repository.match("movie", 42, "Dune", 2021, null, allowRefresh = true)

        verify(api).match(any(), eq("tv"))
    }

    @Test
    fun `a movie match persists the media and movie rows plus the link with its quality`() = runBlocking<Unit> {
        whenever(dao.findLocalMatch("movie", 42)).thenReturn(null)
        whenever(api.match(any(), eq("tv"))).thenReturn(
            CatalogMatchResponseDto(
                status = "matched",
                match = CatalogMatchQualityDto(confidence = 96, method = "title+year", version = 1),
                item = CatalogItemDto(
                    externalId = "5e37ba2a-1cda-4faf-9f10-335b2f6556a7", kind = "movie", title = "Dune",
                    originalTitle = "Dune", releaseYear = 2021, overview = "...", ageRating = 13,
                    posterUrl = "https://image.tmdb.org/t/p/w780/dune.jpg",
                ),
                cache = CatalogCacheDto(stale = false, updatedAt = "2024-01-01T00:00:00+00:00", refreshAfter = "2024-01-08T00:00:00+00:00"),
            ),
        )

        val result = repository.match("movie", 42, "Dune", 2021, "dune-2021")

        assertEquals(96, result.matchOrNull?.confidence)
        assertEquals(13, result.matchOrNull?.ageRating)
        assertEquals(true, result.matchOrNull?.fromNetwork)
        // F45-R5 : la fenêtre `cache.refreshAfter` du backend (2024-01-08T00:00:00Z) doit être
        // persistée, plus jamais écrite `null` inconditionnellement.
        verify(dao).persistItem(
            media = ExternalMediaEntity("5e37ba2a-1cda-4faf-9f10-335b2f6556a7", "movie", 1_000L, 1_000L, 1_704_672_000_000L),
            movie = ExternalMovieEntity(
                externalId = "5e37ba2a-1cda-4faf-9f10-335b2f6556a7", title = "Dune", originalTitle = "Dune",
                overview = "...", posterPath = "https://image.tmdb.org/t/p/w780/dune.jpg", backdropPath = null,
                releaseDate = "2021", runtimeMinutes = null, ageRating = 13,
            ),
            series = null,
        )
        verify(dao).upsertLink(
            ExternalMediaLinkEntity(
                kind = "movie", providerId = 42, externalId = "5e37ba2a-1cda-4faf-9f10-335b2f6556a7",
                linkKey = "dune-2021", confidence = 96, matchMethod = "title+year", matchVersion = 1,
                matchedAt = 1_000L, lastMatchAttemptAt = 1_000L, retryAfter = null,
            ),
        )
    }

    @Test
    fun `a match response without a cache window persists a null refreshAfter rather than throwing`() = runBlocking<Unit> {
        whenever(dao.findLocalMatch("movie", 42)).thenReturn(null)
        whenever(api.match(any(), eq("tv"))).thenReturn(
            CatalogMatchResponseDto(
                status = "matched",
                match = CatalogMatchQualityDto(confidence = 96, method = "title+year", version = 1),
                item = CatalogItemDto(externalId = "5e37ba2a-1cda-4faf-9f10-335b2f6556a7", kind = "movie", title = "Dune"),
                cache = null,
            ),
        )

        repository.match("movie", 42, "Dune", 2021, "dune-2021")

        verify(dao).persistItem(
            media = ExternalMediaEntity("5e37ba2a-1cda-4faf-9f10-335b2f6556a7", "movie", 1_000L, 1_000L, null),
            movie = ExternalMovieEntity("5e37ba2a-1cda-4faf-9f10-335b2f6556a7", "Dune", null, null, null, null, null, null, null),
            series = null,
        )
    }

    @Test
    fun `a series match persists the series row, not the movie one`() = runBlocking<Unit> {
        whenever(dao.findLocalMatch("series", 7)).thenReturn(null)
        whenever(api.match(any(), eq("tv"))).thenReturn(
            CatalogMatchResponseDto(
                status = "matched",
                match = CatalogMatchQualityDto(confidence = 90, method = "title+year", version = 1),
                item = CatalogItemDto(externalId = "6f48cb3b-2ddb-5fbf-a021-446c3f6667b8", kind = "series", title = "Show"),
            ),
        )

        repository.match("series", 7, "Show", 2020, null)

        verify(dao).persistItem(
            media = ExternalMediaEntity("6f48cb3b-2ddb-5fbf-a021-446c3f6667b8", "series", 1_000L, 1_000L, null),
            movie = null,
            series = ExternalSeriesEntity(
                externalId = "6f48cb3b-2ddb-5fbf-a021-446c3f6667b8", name = "Show", originalName = null,
                overview = null, posterPath = null, backdropPath = null, firstAirDate = null,
                lastAirDate = null, inProduction = null, ageRating = null,
            ),
        )
    }

    @Test
    fun `unresolved or not_found responses persist the attempt without an externalId and never loop-retry immediately`() = runBlocking<Unit> {
        whenever(dao.findLocalMatch("movie", 99)).thenReturn(null)
        whenever(api.match(any(), eq("tv"))).thenReturn(CatalogMatchResponseDto(status = "unresolved", item = null))

        val result = repository.match("movie", 99, "Unknown", null, null)

        assertEquals(ExternalMetadataMatchOutcome.Unresolved, result)
        verify(dao, never()).upsertMedia(any())
        val link = argumentCaptor<ExternalMediaLinkEntity>()
        verify(dao).upsertLink(link.capture())
        assertNull(link.firstValue.externalId)
        assertEquals(1_000L, link.firstValue.lastMatchAttemptAt)
        assertTrue(link.firstValue.retryAfter!! > 1_000L)
    }

    @Test
    fun `matching requests only send kind title and year`() = runBlocking<Unit> {
        whenever(dao.findLocalMatch(any(), any())).thenReturn(null)
        whenever(api.match(any(), any())).thenReturn(CatalogMatchResponseDto(status = "unresolved", item = null))
        val request = argumentCaptor<CatalogMatchRequestDto>()

        repository.match("movie", 1, "The Thing", 1982, null, ExternalMatchHints(director = "John Carpenter"))
        repository.match("movie", 2, "No Hints", null, null, ExternalMatchHints())
        verify(api, times(2)).match(request.capture(), any())

        assertNull(request.firstValue.hints)
        assertNull(request.secondValue.hints)
    }

    @Test
    fun `batch sends compact requests and preserves their response order`() = runBlocking<Unit> {
        whenever(dao.findLocalMatch(any(), any())).thenReturn(null)
        whenever(api.matchBatch(any(), eq("tv"))).thenReturn(
            CatalogMatchBatchResponseDto(
                items = listOf(
                    CatalogMatchResponseDto(status = "matched", item = CatalogItemDto(externalId = "5e37ba2a-1cda-4faf-9f10-335b2f6556a7", kind = "movie", title = "First")),
                    CatalogMatchResponseDto(status = "not_found"),
                ),
            ),
        )

        val result = repository.matchBatch(
            listOf(
                ExternalMetadataMatchRequest("movie", 1, "First", 2021, "first"),
                ExternalMetadataMatchRequest("series", 2, "Missing", null, "missing"),
            ),
        )

        assertEquals("5e37ba2a-1cda-4faf-9f10-335b2f6556a7", result[0].matchOrNull?.externalId)
        assertEquals(ExternalMetadataMatchOutcome.Unresolved, result[1])
        val request = argumentCaptor<com.cstv.app.data.remote.dto.CatalogMatchBatchRequestDto>()
        verify(api).matchBatch(request.capture(), eq("tv"))
        assertEquals(listOf("First", "Missing"), request.firstValue.items.map { it.title })
        assertEquals(listOf(2021, null), request.firstValue.items.map { it.year })
        assertTrue(request.firstValue.items.all { it.hints == null })
    }

    @Test
    fun `T29 a retry status never persists a link and converts retryAfter seconds to millis`() = runBlocking<Unit> {
        whenever(dao.findLocalMatch("movie", 42)).thenReturn(null)
        whenever(api.match(any(), eq("tv"))).thenReturn(
            CatalogMatchResponseDto(status = "retry", item = null, cache = CatalogCacheDto(retryAfter = 3)),
        )

        val result = repository.match("movie", 42, "Dune", 2021, "dune-2021")

        assertEquals(ExternalMetadataMatchOutcome.Retry(3_000L), result)
        verify(dao, never()).upsertLink(any())
    }

    @Test
    fun `T29 a retry status with no retryAfter still yields Retry with a null delay`() = runBlocking<Unit> {
        whenever(dao.findLocalMatch("movie", 42)).thenReturn(null)
        whenever(api.match(any(), eq("tv"))).thenReturn(CatalogMatchResponseDto(status = "retry", item = null, cache = null))

        val result = repository.match("movie", 42, "Dune", 2021, null)

        assertEquals(ExternalMetadataMatchOutcome.Retry(null), result)
    }

    @Test
    fun `T29 a batch preserves order when one item is retry and never persists it as unresolved`() = runBlocking<Unit> {
        whenever(dao.findLocalMatch(any(), any())).thenReturn(null)
        whenever(api.matchBatch(any(), eq("tv"))).thenReturn(
            CatalogMatchBatchResponseDto(
                items = listOf(
                    CatalogMatchResponseDto(status = "matched", item = CatalogItemDto(externalId = "5e37ba2a-1cda-4faf-9f10-335b2f6556a7", kind = "movie", title = "First")),
                    CatalogMatchResponseDto(status = "retry", item = null, cache = CatalogCacheDto(retryAfter = 5)),
                    CatalogMatchResponseDto(status = "not_found"),
                ),
            ),
        )

        val result = repository.matchBatch(
            listOf(
                ExternalMetadataMatchRequest("movie", 1, "First", 2021, "first"),
                ExternalMetadataMatchRequest("movie", 2, "Retry Me", 2021, "retry"),
                ExternalMetadataMatchRequest("series", 3, "Missing", null, "missing"),
            ),
        )

        assertEquals("5e37ba2a-1cda-4faf-9f10-335b2f6556a7", result[0].matchOrNull?.externalId)
        assertEquals(ExternalMetadataMatchOutcome.Retry(5_000L), result[1])
        assertEquals(ExternalMetadataMatchOutcome.Unresolved, result[2])
    }

    // -------------------------------------------------------------------------------------------
    // T29 débit §2 : le 429 de throttle CSTV est traduit ici, jamais lu par le worker.
    // -------------------------------------------------------------------------------------------

    private fun httpException(code: Int, retryAfter: String? = null): HttpException {
        val raw = okhttp3.Response.Builder()
            .code(code)
            .message("error")
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .request(okhttp3.Request.Builder().url("https://cstv.example/v1/catalog/matches/batch").build())
            .apply { retryAfter?.let { header("Retry-After", it) } }
            .build()
        return HttpException(retrofit2.Response.error<Any>("{}".toResponseBody("application/json".toMediaType()), raw))
    }

    @Test
    fun `T29 debit a batch throttled with 429 raises CatalogThrottledException carrying the Retry-After in millis`() = runBlocking<Unit> {
        whenever(dao.findLocalMatch(any(), any())).thenReturn(null)
        whenever(api.matchBatch(any(), eq("tv"))).thenAnswer { throw httpException(429, "45") }

        val error = assertThrows(CatalogThrottledException::class.java) {
            runBlocking { repository.matchBatch(listOf(ExternalMetadataMatchRequest("movie", 1, "First", 2021, "first"))) }
        }

        assertEquals(45_000L, error.retryAfterMillis)
        verify(dao, never()).upsertLink(any())
    }

    @Test
    fun `T29 debit a 429 without a parsable Retry-After yields a null delay rather than a guessed one`() = runBlocking<Unit> {
        whenever(dao.findLocalMatch(any(), any())).thenReturn(null)
        whenever(api.matchBatch(any(), eq("tv"))).thenAnswer { throw httpException(429, "Wed, 21 Oct 2015 07:28:00 GMT") }

        val error = assertThrows(CatalogThrottledException::class.java) {
            runBlocking { repository.matchBatch(listOf(ExternalMetadataMatchRequest("movie", 1, "First", 2021, "first"))) }
        }

        assertNull(error.retryAfterMillis)
    }

    @Test
    fun `T29 debit the single match path is throttle-aware too`() = runBlocking<Unit> {
        whenever(dao.findLocalMatch("movie", 42)).thenReturn(null)
        whenever(api.match(any(), eq("tv"))).thenAnswer { throw httpException(429, "12") }

        val error = assertThrows(CatalogThrottledException::class.java) {
            runBlocking { repository.match("movie", 42, "Dune", 2021, "dune-2021") }
        }

        assertEquals(12_000L, error.retryAfterMillis)
    }

    @Test
    fun `T29 debit a non-429 HTTP error is never mistaken for a throttle`() = runBlocking<Unit> {
        whenever(dao.findLocalMatch(any(), any())).thenReturn(null)
        whenever(api.matchBatch(any(), eq("tv"))).thenAnswer { throw httpException(503, "45") }

        assertThrows(HttpException::class.java) {
            runBlocking { repository.matchBatch(listOf(ExternalMetadataMatchRequest("movie", 1, "First", 2021, "first"))) }
        }
    }

    @Test
    fun `F45-R7 series seasons are fetched and persisted only through the explicit detail method`() = runBlocking<Unit> {
        val externalId = "5e37ba2a-1cda-4faf-9f10-335b2f6556a7"
        whenever(dao.refreshAfterForMedia(externalId)).thenReturn(10_000L)
        whenever(api.item(externalId, "tv")).thenReturn(
            CatalogItemDto(externalId = externalId, kind = "series", title = "Show", numberOfSeasons = 1),
        )
        whenever(api.season(externalId, 0, "tv")).thenReturn(CatalogSeasonDto(seasonNumber = 0, name = "Specials"))
        whenever(api.season(externalId, 1, "tv")).thenReturn(
            CatalogSeasonDto(
                seasonNumber = 1,
                name = "Season 1",
                episodes = listOf(CatalogEpisodeDto(episodeNumber = 1, name = "Pilot", runtimeMinutes = 42)),
            ),
        )

        repository.hydrateSeriesSeasons(externalId)

        verify(api).season(externalId, 0, "tv")
        verify(api).season(externalId, 1, "tv")
        verify(dao, times(2)).persistSeason(any(), any())
    }

    @Test
    fun `F46 observeCoverage maps the DAO projection into the domain model without any network call`() = runBlocking<Unit> {
        val projection = com.cstv.app.data.local.dao.ExternalMetadataCoverageProjection(
            movieTotal = 100, movieLinked = 80, movieProcessed = 90,
            seriesTotal = 50, seriesLinked = 40, seriesProcessed = 45,
        )
        whenever(dao.observeCoverage()).thenReturn(kotlinx.coroutines.flow.flowOf(projection))

        val coverage = repository.observeCoverage().first()

        assertEquals(150, coverage.total)
        assertEquals(120, coverage.linked)
        assertEquals(15, coverage.unresolved) // (90-80) + (45-40)
        assertEquals(135, coverage.processed)
        assertEquals(15, coverage.pending)

        assertEquals(100, coverage.movies.total)
        assertEquals(80, coverage.movies.linked)
        assertEquals(10, coverage.movies.unresolved)

        assertEquals(50, coverage.series.total)
        assertEquals(40, coverage.series.linked)
        assertEquals(5, coverage.series.unresolved)

        verifyNoInteractions(api)
    }

    @Test
    fun `F46 observeCoverage defensively floors unresolved at zero on inconsistent counters`() = runBlocking<Unit> {
        val projection = com.cstv.app.data.local.dao.ExternalMetadataCoverageProjection(
            movieTotal = 10, movieLinked = 10, movieProcessed = 5, // processed < linked : incohérent
            seriesTotal = 0, seriesLinked = 0, seriesProcessed = 0,
        )
        whenever(dao.observeCoverage()).thenReturn(kotlinx.coroutines.flow.flowOf(projection))

        val coverage = repository.observeCoverage().first()

        assertEquals(0, coverage.movies.unresolved)
    }

    @Test
    fun `F46 observeCoverage represents an empty catalogue with total zero`() = runBlocking<Unit> {
        val projection = com.cstv.app.data.local.dao.ExternalMetadataCoverageProjection(0, 0, 0, 0, 0, 0)
        whenever(dao.observeCoverage()).thenReturn(kotlinx.coroutines.flow.flowOf(projection))

        val coverage = repository.observeCoverage().first()

        assertEquals(0, coverage.total)
        assertEquals(0, coverage.processed)
        assertEquals(0, coverage.pending)
    }
}
