package com.cstv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.cstv.app.data.local.entity.ExternalHydrationRequestEntity
import com.cstv.app.data.local.entity.ExternalMediaEntity
import com.cstv.app.data.local.entity.ExternalMediaLinkEntity
import com.cstv.app.data.local.entity.ExternalMovieEntity
import com.cstv.app.data.local.entity.ExternalSeriesEntity
import com.cstv.app.data.local.entity.ExternalSeasonEntity
import com.cstv.app.data.local.entity.ExternalEpisodeEntity
import com.cstv.app.data.local.entity.ExternalGenreEntity
import com.cstv.app.data.local.entity.ExternalKeywordEntity
import com.cstv.app.data.local.entity.ExternalOriginCountryEntity
import com.cstv.app.data.local.entity.ExternalEpisodeRuntimeEntity
import com.cstv.app.data.local.entity.ExternalAlternativeTitleEntity
import com.cstv.app.data.local.entity.ExternalRecommendationEntity
import com.cstv.app.data.local.entity.ExternalVideoEntity

@Dao
interface ExternalMetadataDao {
    @Query("SELECT * FROM external_media_links WHERE externalId IN (:externalIds)")
    suspend fun findLinksByExternalIds(externalIds: List<String>): List<ExternalMediaLinkEntity>

    @Query("SELECT * FROM external_media_links WHERE kind = :kind AND providerId = :providerId LIMIT 1")
    suspend fun findLink(kind: String, providerId: Int): ExternalMediaLinkEntity?

    /**
     * F45-R4 : lecture jointe lien + fiche (film ou série) pour un hit local — un lien déjà résolu
     * ne doit pas perdre l'âge/la qualité de match déjà persistés. Avant ce correctif, le
     * court-circuit local de [ExternalMetadataRepositoryImpl] ignorait `external_movies`/
     * `external_series` et renvoyait systématiquement `ageRating = null`, ce qui faisait
     * redevenir `UNCLASSIFIED` un média pourtant déjà classifié dès l'expiration du cache mémoire
     * de `ContentClassificationRepository` (30 min) ou un redémarrage du process — F44 exigeait
     * alors un PIN indu.
     *
     * `refreshAfter` vient de `external_media` (F45-R5) : c'est ce qui permet à l'appelant de
     * décider si ce hit local doit être resservi tel quel ou déclencher un rafraîchissement.
     */
    @Query(
        "SELECT l.externalId AS externalId, l.confidence AS confidence, l.matchMethod AS matchMethod, " +
            "l.matchVersion AS matchVersion, COALESCE(mv.ageRating, sr.ageRating) AS ageRating, " +
            "me.refreshAfter AS refreshAfter " +
            "FROM external_media_links l " +
            "LEFT JOIN external_media me ON me.externalId = l.externalId " +
            "LEFT JOIN external_movies mv ON mv.externalId = l.externalId " +
            "LEFT JOIN external_series sr ON sr.externalId = l.externalId " +
            "WHERE l.kind = :kind AND l.providerId = :providerId AND l.externalId IS NOT NULL LIMIT 1",
    )
    suspend fun findLocalMatch(kind: String, providerId: Int): LocalMatchProjection?

    @Query("SELECT refreshAfter FROM external_media WHERE externalId = :externalId")
    suspend fun refreshAfterForMedia(externalId: String): Long?

    @Query("SELECT * FROM external_hydration_queue WHERE nextAttemptAt <= :now ORDER BY priority DESC, createdAt ASC LIMIT 1")
    suspend fun nextRequest(now: Long): ExternalHydrationRequestEntity?

    @Query("DELETE FROM external_hydration_queue WHERE kind = :kind AND providerId = :providerId")
    suspend fun deleteRequest(kind: String, providerId: Int)

    @Query("SELECT priority FROM external_hydration_queue WHERE kind = :kind AND providerId = :providerId")
    suspend fun priorityOf(kind: String, providerId: Int): Int?

    /**
     * F45-R2 : réveil du prochain item en backoff. `null` si la file est vide — le worker ne doit
     * alors programmer aucune continuation.
     */
    @Query("SELECT MIN(nextAttemptAt) FROM external_hydration_queue")
    suspend fun earliestNextAttemptAt(): Long?

    @Upsert suspend fun upsertMedia(media: ExternalMediaEntity)
    @Upsert suspend fun upsertMovie(movie: ExternalMovieEntity)
    @Upsert suspend fun upsertSeries(series: ExternalSeriesEntity)
    @Upsert suspend fun upsertSeasons(seasons: List<ExternalSeasonEntity>)
    @Upsert suspend fun upsertEpisodes(episodes: List<ExternalEpisodeEntity>)
    @Upsert suspend fun upsertGenres(genres: List<ExternalGenreEntity>)
    @Upsert suspend fun upsertKeywords(keywords: List<ExternalKeywordEntity>)
    @Upsert suspend fun upsertOriginCountries(countries: List<ExternalOriginCountryEntity>)
    @Upsert suspend fun upsertEpisodeRuntimes(runtimes: List<ExternalEpisodeRuntimeEntity>)
    @Upsert suspend fun upsertAlternativeTitles(titles: List<ExternalAlternativeTitleEntity>)
    @Upsert suspend fun upsertRecommendations(recommendations: List<ExternalRecommendationEntity>)
    @Upsert suspend fun upsertVideos(videos: List<ExternalVideoEntity>)
    @Upsert suspend fun upsertLink(link: ExternalMediaLinkEntity)
    @Upsert suspend fun upsertRequest(request: ExternalHydrationRequestEntity)

    @Query("DELETE FROM external_media_genres WHERE externalId = :externalId") suspend fun deleteGenres(externalId: String)
    @Query("DELETE FROM external_media_keywords WHERE externalId = :externalId") suspend fun deleteKeywords(externalId: String)
    @Query("DELETE FROM external_media_origin_countries WHERE externalId = :externalId") suspend fun deleteOriginCountries(externalId: String)
    @Query("DELETE FROM external_series_episode_runtimes WHERE externalId = :externalId") suspend fun deleteEpisodeRuntimes(externalId: String)
    @Query("DELETE FROM external_alternative_titles WHERE externalId = :externalId") suspend fun deleteAlternativeTitles(externalId: String)
    @Query("DELETE FROM external_recommendations WHERE externalId = :externalId") suspend fun deleteRecommendations(externalId: String)
    @Query("DELETE FROM external_videos WHERE externalId = :externalId") suspend fun deleteVideos(externalId: String)
    @Query("DELETE FROM external_episodes WHERE externalId = :externalId AND seasonNumber = :seasonNumber") suspend fun deleteEpisodes(externalId: String, seasonNumber: Int)

    /**
     * F45-R12 : lecture de la priorité existante + upsert conditionnel regroupés dans une seule
     * transaction Room (`@Transaction` sur une méthode par défaut suspend, motif documenté Room
     * pour composer plusieurs requêtes DAO atomiquement). SQLite sérialise les écritures : deux
     * appels concurrents ne peuvent plus s'entrelacer et faire perdre une promotion `DETAIL_OPEN`
     * au profit d'une demande de fond arrivée en même temps (contrairement à un
     * `SELECT` + `UPSERT` séparés côté appelant).
     */
    @Transaction
    suspend fun upsertRequestIfHigherPriority(request: ExternalHydrationRequestEntity) {
        val existingPriority = priorityOf(request.kind, request.providerId)
        if (isHigherPriority(existingPriority, request.priority)) {
            upsertRequest(request)
        }
    }

    /**
     * Le backfill et une synchronisation IPTV peuvent produire beaucoup de nouveaux médias d'un
     * coup. Conserver les promotions dans une seule transaction évite une écriture Room par item
     * hors transaction, tout en laissant le scheduler ne réveiller WorkManager qu'une fois pour le
     * lot entier (F45-R6).
     */
    @Transaction
    suspend fun upsertRequestsIfHigherPriority(requests: List<ExternalHydrationRequestEntity>) {
        requests.forEach { request -> upsertRequestIfHigherPriority(request) }
    }

    /**
     * F45-R7 : l'identité, la fiche et son niveau spécifique doivent apparaître ensemble après un
     * process death. Room ne rend pas trois `@Upsert` consécutifs atomiques par défaut ; cette
     * frontière transactionnelle laisse l'ancienne fiche intacte si l'écriture de remplacement
     * échoue au milieu.
     */
    @Transaction
    suspend fun persistItem(
        media: ExternalMediaEntity,
        movie: ExternalMovieEntity? = null,
        series: ExternalSeriesEntity? = null,
        genres: List<ExternalGenreEntity> = emptyList(),
        keywords: List<ExternalKeywordEntity> = emptyList(),
        originCountries: List<ExternalOriginCountryEntity> = emptyList(),
        episodeRuntimes: List<ExternalEpisodeRuntimeEntity> = emptyList(),
        alternativeTitles: List<ExternalAlternativeTitleEntity> = emptyList(),
        recommendations: List<ExternalRecommendationEntity> = emptyList(),
        videos: List<ExternalVideoEntity> = emptyList(),
    ) {
        require((movie == null) != (series == null)) { "Exactly one external media detail is required." }
        upsertMedia(media)
        movie?.let { upsertMovie(it) }
        series?.let { upsertSeries(it) }
        deleteGenres(media.externalId); upsertGenres(genres)
        deleteKeywords(media.externalId); upsertKeywords(keywords)
        deleteOriginCountries(media.externalId); upsertOriginCountries(originCountries)
        deleteEpisodeRuntimes(media.externalId); upsertEpisodeRuntimes(episodeRuntimes)
        deleteAlternativeTitles(media.externalId); upsertAlternativeTitles(alternativeTitles)
        deleteRecommendations(media.externalId); upsertRecommendations(recommendations)
        deleteVideos(media.externalId); upsertVideos(videos)
    }

    /** Saison et épisodes sont remplacés atomiquement uniquement après une fiche série ouverte. */
    @Transaction
    suspend fun persistSeason(season: ExternalSeasonEntity, episodes: List<ExternalEpisodeEntity>) {
        upsertSeasons(listOf(season))
        deleteEpisodes(season.externalId, season.seasonNumber)
        upsertEpisodes(episodes)
    }

    /**
     * F45 §8.11 : films sans état de résolution F45 — jamais tentés, OU `UNRESOLVED` dont le
     * cooldown (`retryAfter`) est passé (§8.9, jamais retenté en boucle). Exclut ce qui est déjà en
     * file pour ne pas la reproposer inutilement à chaque passage. Pagination par clé (`streamId`),
     * pas par `OFFSET` : stable même si le catalogue change entre deux pages (§8.9 "aucun scan ne
     * matérialise ~54 000 lignes en mémoire").
     */
    @Query(
        "SELECT streamId AS providerId, linkKey FROM vod_streams WHERE streamId > :afterId " +
            "AND NOT EXISTS (SELECT 1 FROM external_media_links l WHERE l.kind = 'movie' AND l.providerId = vod_streams.streamId " +
            "AND (l.externalId IS NOT NULL OR l.retryAfter IS NULL OR l.retryAfter > :now)) " +
            "AND NOT EXISTS (SELECT 1 FROM external_hydration_queue q WHERE q.kind = 'movie' AND q.providerId = vod_streams.streamId) " +
            "ORDER BY streamId LIMIT :limit",
    )
    suspend fun findMoviesMissingExternalMetadata(afterId: Int, now: Long, limit: Int): List<BackfillCandidate>

    /** Équivalent séries — voir [findMoviesMissingExternalMetadata]. */
    @Query(
        "SELECT seriesId AS providerId, linkKey FROM series_streams WHERE seriesId > :afterId " +
            "AND NOT EXISTS (SELECT 1 FROM external_media_links l WHERE l.kind = 'series' AND l.providerId = series_streams.seriesId " +
            "AND (l.externalId IS NOT NULL OR l.retryAfter IS NULL OR l.retryAfter > :now)) " +
            "AND NOT EXISTS (SELECT 1 FROM external_hydration_queue q WHERE q.kind = 'series' AND q.providerId = series_streams.seriesId) " +
            "ORDER BY seriesId LIMIT :limit",
    )
    suspend fun findSeriesMissingExternalMetadata(afterId: Int, now: Long, limit: Int): List<BackfillCandidate>
}

/** Projection légère pour le backfill — jamais la ligne complète (titre/plot/...), juste de quoi mettre en file et dédupliquer. */
data class BackfillCandidate(val providerId: Int, val linkKey: String)

/** F45-R4/R5 : ce qu'un hit local peut resservir sans réseau — voir [ExternalMetadataDao.findLocalMatch]. */
data class LocalMatchProjection(
    val externalId: String,
    val confidence: Int?,
    val matchMethod: String?,
    val matchVersion: Int?,
    val ageRating: Int?,
    val refreshAfter: Long?,
)

/**
 * F45-R12 : décision pure, extraite de [ExternalMetadataDao.upsertRequestIfHigherPriority] pour
 * rester testable sans passer par la transaction Room. Une priorité strictement supérieure promeut
 * (ex. `DETAIL_OPEN` sur une entrée `MISSING_METADATA`) ; une priorité égale ou inférieure
 * n'écrase jamais une demande déjà mieux priorisée.
 */
internal fun isHigherPriority(existingPriority: Int?, newPriority: Int): Boolean =
    existingPriority == null || existingPriority < newPriority
