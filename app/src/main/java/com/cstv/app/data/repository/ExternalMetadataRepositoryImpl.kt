package com.cstv.app.data.repository

import com.cstv.app.data.local.dao.ExternalMetadataDao
import com.cstv.app.data.local.entity.ExternalMediaEntity
import com.cstv.app.data.local.entity.ExternalMediaLinkEntity
import com.cstv.app.data.local.entity.ExternalMovieEntity
import com.cstv.app.data.local.entity.ExternalSeriesEntity
import com.cstv.app.data.local.entity.ExternalGenreEntity
import com.cstv.app.data.local.entity.ExternalKeywordEntity
import com.cstv.app.data.local.entity.ExternalOriginCountryEntity
import com.cstv.app.data.local.entity.ExternalEpisodeRuntimeEntity
import com.cstv.app.data.local.entity.ExternalAlternativeTitleEntity
import com.cstv.app.data.local.entity.ExternalRecommendationEntity
import com.cstv.app.data.local.entity.ExternalVideoEntity
import com.cstv.app.data.local.entity.ExternalSeasonEntity
import com.cstv.app.data.local.entity.ExternalEpisodeEntity
import com.cstv.app.data.remote.api.CstvCatalogApiService
import com.cstv.app.data.remote.api.DeviceTypeProvider
import com.cstv.app.data.remote.dto.CatalogItemDto
import com.cstv.app.data.remote.dto.CatalogMatchBatchRequestDto
import com.cstv.app.data.remote.dto.CatalogMatchRequestDto
import com.cstv.app.data.remote.dto.CatalogMatchResponseDto
import com.cstv.app.domain.model.ExternalMatchHints
import com.cstv.app.domain.model.ExternalMetadataMatch
import com.cstv.app.domain.model.ExternalMetadataMatchRequest
import com.cstv.app.domain.repository.ExternalMetadataRepository
import com.cstv.app.domain.util.IsoInstant
import com.cstv.app.domain.util.TimeProvider
import javax.inject.Inject

class ExternalMetadataRepositoryImpl @Inject constructor(
    private val api: CstvCatalogApiService,
    private val dao: ExternalMetadataDao,
    private val deviceType: DeviceTypeProvider,
    private val timeProvider: TimeProvider,
) : ExternalMetadataRepository {

    override suspend fun hydrateSeriesSeasons(externalId: String) {
        val now = timeProvider.nowMillis()
        val item = api.item(externalId, deviceType.current())
        if (item.kind != "series") return
        // `item` enrichit les sous-ressources de niveau série sans rendre la fiche bloquante. La
        // fenêtre déjà reçue du match reste intacte : cette route ne renvoie pas d'enveloppe cache.
        persistItem("series", externalId, item, now, dao.refreshAfterForMedia(externalId))
        val seasonCount = item.numberOfSeasons?.coerceIn(0, MAX_SEASONS_PER_DETAIL_OPEN) ?: return
        for (seasonNumber in 0..seasonCount) {
            val season = api.season(externalId, seasonNumber, deviceType.current())
            if (season.seasonNumber == null) continue
            dao.persistSeason(
                ExternalSeasonEntity(
                    externalId = externalId,
                    seasonNumber = season.seasonNumber,
                    name = season.name.orEmpty(),
                    overview = season.overview,
                    posterPath = season.posterUrl,
                    airDate = season.airDate,
                    voteAverage = season.voteAverage,
                    refreshedAt = now,
                    refreshAfter = null,
                ),
                season.episodes.orEmpty().mapNotNull { episode ->
                    val episodeNumber = episode.episodeNumber ?: return@mapNotNull null
                    ExternalEpisodeEntity(
                        externalId = externalId,
                        seasonNumber = season.seasonNumber,
                        episodeNumber = episodeNumber,
                        name = episode.name.orEmpty(),
                        overview = episode.overview,
                        stillPath = episode.stillUrl,
                        airDate = episode.airDate,
                        runtimeMinutes = episode.runtimeMinutes,
                        voteAverage = episode.voteAverage,
                        voteCount = episode.voteCount,
                    )
                },
            )
        }
    }

    override suspend fun match(
        kind: String,
        providerId: Int,
        title: String,
        year: Int?,
        linkKey: String?,
        hints: ExternalMatchHints,
        allowRefresh: Boolean,
    ): ExternalMetadataMatch? {
        dao.findLocalMatch(kind, providerId)?.let { local ->
            // F45-R5 : hors DETAIL_OPEN, jamais de rafraîchissement (§7.1/§7.5) — un lien local est
            // toujours resservi tel quel, même stale. `allowRefresh` seul peut faire retomber sur le
            // réseau ci-dessous, et seulement si `refreshAfter` est effectivement dépassé.
            if (!allowRefresh || !isStale(local.refreshAfter)) {
                return ExternalMetadataMatch(local.externalId, kind, local.confidence, local.matchMethod, local.matchVersion, local.ageRating, fromNetwork = false)
            }
        }

        val response = api.match(CatalogMatchRequestDto(kind = kind, title = title, year = year), deviceType.current())
        return persistNetworkMatch(kind, providerId, linkKey, response)
    }

    override suspend fun matchBatch(requests: List<ExternalMetadataMatchRequest>): List<ExternalMetadataMatch?> {
        if (requests.isEmpty()) return emptyList()
        val results = arrayOfNulls<ExternalMetadataMatch>(requests.size)
        val remote = mutableListOf<Pair<Int, ExternalMetadataMatchRequest>>()
        requests.forEachIndexed { index, request ->
            val local = dao.findLocalMatch(request.kind, request.providerId)
            if (local != null && (!request.allowRefresh || !isStale(local.refreshAfter))) {
                results[index] = ExternalMetadataMatch(local.externalId, request.kind, local.confidence, local.matchMethod, local.matchVersion, local.ageRating, fromNetwork = false)
            } else {
                remote += index to request
            }
        }
        if (remote.isNotEmpty()) {
            val response = api.matchBatch(
                CatalogMatchBatchRequestDto(remote.map { (_, request) -> CatalogMatchRequestDto(kind = request.kind, title = request.title, year = request.year) }),
                deviceType.current(),
            )
            val items = response.items ?: throw IllegalStateException("Catalog match batch returned no items")
            check(items.size == remote.size) { "Catalog match batch response size mismatch" }
            remote.forEachIndexed { responseIndex, (requestIndex, request) ->
                results[requestIndex] = persistNetworkMatch(request.kind, request.providerId, request.linkKey, items[responseIndex])
            }
        }
        return results.toList()
    }

    private suspend fun persistNetworkMatch(kind: String, providerId: Int, linkKey: String?, response: CatalogMatchResponseDto): ExternalMetadataMatch? {
        val now = timeProvider.nowMillis()
        val item = response.item
        val externalId = item?.externalId
        if (item == null || externalId == null) {
            // §8.9 : un média UNRESOLVED/not_found n'est pas "jamais tenté" — persister la tentative
            // (sans externalId) évite au backfill de le remettre en file en boucle (§7.5/§7.1).
            persistUnresolvedAttempt(kind, providerId, linkKey, response.match?.version, now)
            return null
        }
        val quality = response.match
        // F45-R5 : la fenêtre vient du backend (§7.10, le client ne duplique jamais les règles de
        // TTL) — avant ce correctif, `refreshAfter` était toujours écrit `null`, si bien qu'aucune
        // métadonnée hydratée ne pouvait jamais être détectée comme stale.
        val refreshAfter = IsoInstant.parseMillis(response.cache?.refreshAfter)

        persistItem(kind, externalId, item, now, refreshAfter)
        dao.upsertLink(
            ExternalMediaLinkEntity(
                kind = kind,
                providerId = providerId,
                externalId = externalId,
                linkKey = linkKey,
                confidence = quality?.confidence,
                matchMethod = quality?.method,
                matchVersion = quality?.version,
                matchedAt = now,
                lastMatchAttemptAt = now,
                retryAfter = null,
            ),
        )

        return ExternalMetadataMatch(externalId, kind, quality?.confidence, quality?.method, quality?.version, item.ageRating)
    }

    private suspend fun persistUnresolvedAttempt(kind: String, providerId: Int, linkKey: String?, matchVersion: Int?, now: Long) {
        dao.upsertLink(
            ExternalMediaLinkEntity(
                kind = kind, providerId = providerId, externalId = null, linkKey = linkKey,
                confidence = null, matchMethod = null, matchVersion = matchVersion,
                matchedAt = null, lastMatchAttemptAt = now, retryAfter = now + UNRESOLVED_RETRY_COOLDOWN_MS,
            ),
        )
    }

    /** Persiste la fiche niveau média — jamais les saisons/épisodes ici (§7.5, réservés à l'ouverture de fiche série). */
    private suspend fun persistItem(kind: String, externalId: String, item: CatalogItemDto, now: Long, refreshAfter: Long?) {
        val media = ExternalMediaEntity(externalId = externalId, kind = kind, createdAt = now, refreshedAt = now, refreshAfter = refreshAfter)
        if (kind == "movie") {
            dao.persistItem(
                media = media,
                movie = ExternalMovieEntity(
                    externalId = externalId,
                    title = item.title.orEmpty(),
                    originalTitle = item.originalTitle,
                    overview = item.overview,
                    posterPath = item.posterUrl,
                    backdropPath = item.backdropUrl,
                    releaseDate = item.releaseYear?.toString(),
                    runtimeMinutes = null,
                    ageRating = item.ageRating,
                ),
                genres = item.genres.toExternalGenres(externalId),
                keywords = item.keywords.toExternalKeywords(externalId),
                originCountries = item.originCountries.toExternalCountries(externalId),
                alternativeTitles = item.alternativeTitles.toExternalTitles(externalId),
                recommendations = item.recommendations.toExternalRecommendations(externalId),
                videos = item.videos.toExternalVideos(externalId),
            )
        } else {
            dao.persistItem(
                media = media,
                series = ExternalSeriesEntity(
                    externalId = externalId,
                    name = item.title.orEmpty(),
                    originalName = item.originalTitle,
                    overview = item.overview,
                    posterPath = item.posterUrl,
                    backdropPath = item.backdropUrl,
                    firstAirDate = item.releaseYear?.toString(),
                    lastAirDate = null,
                    inProduction = null,
                    ageRating = item.ageRating,
                ),
                genres = item.genres.toExternalGenres(externalId),
                keywords = item.keywords.toExternalKeywords(externalId),
                originCountries = item.originCountries.toExternalCountries(externalId),
                episodeRuntimes = item.episodeRunTimes.toExternalRuntimes(externalId),
                alternativeTitles = item.alternativeTitles.toExternalTitles(externalId),
                recommendations = item.recommendations.toExternalRecommendations(externalId),
                videos = item.videos.toExternalVideos(externalId),
            )
        }
    }

    private fun List<String>?.toExternalGenres(externalId: String) = orEmpty().filter(String::isNotBlank).distinct().map { ExternalGenreEntity(externalId, it) }
    private fun List<String>?.toExternalKeywords(externalId: String) = orEmpty().filter(String::isNotBlank).distinct().map { ExternalKeywordEntity(externalId, it) }
    private fun List<String>?.toExternalCountries(externalId: String) = orEmpty().filter(String::isNotBlank).distinct().map { ExternalOriginCountryEntity(externalId, it) }
    private fun List<String>?.toExternalTitles(externalId: String) = orEmpty().filter(String::isNotBlank).distinct().map { ExternalAlternativeTitleEntity(externalId, it) }
    private fun List<String>?.toExternalRecommendations(externalId: String) = orEmpty().take(20).mapIndexed { index, id -> ExternalRecommendationEntity(externalId, index, id) }
    private fun List<Int>?.toExternalRuntimes(externalId: String) = orEmpty().filter { it > 0 }.distinct().map { ExternalEpisodeRuntimeEntity(externalId, it) }
    private fun List<com.cstv.app.data.remote.dto.CatalogVideoDto>?.toExternalVideos(externalId: String) = orEmpty()
        .filter { !it.key.isNullOrBlank() && !it.site.isNullOrBlank() }
        .distinctBy { it.key }
        .map { ExternalVideoEntity(externalId, it.key!!, it.site!!, it.type, it.name, it.official == true) }

    /** F45-R5 : `refreshAfter == null` (jamais hydraté avec fenêtre connue, ex. lignes créées avant ce correctif) compte comme stale — se corrige tout seul au prochain hit `allowRefresh`. */
    private fun isStale(refreshAfter: Long?): Boolean = refreshAfter == null || refreshAfter <= timeProvider.nowMillis()

    companion object {
        /** §8.9/§6 : "UNRESOLVED ~1-7j", cooldown ajustable — retenu médian pour ne pas rescanner en boucle sans surexposer. */
        internal const val UNRESOLVED_RETRY_COOLDOWN_MS = 3L * 24 * 60 * 60 * 1000
        /** Garde-fou défensif : une série incohérente ne doit jamais créer une hydratation sans fin. */
        private const val MAX_SEASONS_PER_DETAIL_OPEN = 100
    }

}
