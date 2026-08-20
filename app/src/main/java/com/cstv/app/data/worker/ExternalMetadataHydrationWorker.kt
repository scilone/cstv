package com.cstv.app.data.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cstv.app.data.local.dao.ExternalMetadataDao
import com.cstv.app.data.local.dao.SeriesDao
import com.cstv.app.data.local.dao.VodDao
import com.cstv.app.data.local.entity.ExternalHydrationRequestEntity
import com.cstv.app.data.local.entity.ExternalMediaLinkEntity
import com.cstv.app.di.IptvLog
import com.cstv.app.domain.model.ExternalMatchHints
import com.cstv.app.domain.model.ExternalMetadataMatch
import com.cstv.app.domain.model.GenreParser
import com.cstv.app.domain.model.HydrationReason
import com.cstv.app.domain.repository.ExternalMetadataRepository
import com.cstv.app.domain.util.TimeProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * F45 §8.11 : draine `external_hydration_queue` séquentiellement — une seule hydratation active à
 * la fois (`ExistingWorkPolicy.KEEP` empêche toute exécution concurrente sous le même nom), un item
 * à la fois, jamais en parallèle (§9.3 "jamais de parallélisme massif"). Un item en échec est
 * reprogrammé avec un backoff croissant plutôt que retiré de la file : les suivants continuent
 * d'être traités (validation Tâche 7 : "erreur item ne bloque pas suivants").
 *
 * Saisons/épisodes : non hydratés par ce worker — aucune table Room dédiée n'existe encore pour eux
 * (F45 Tâche 5 §11, scope volontairement limité au niveau média). Cohérent avec §7.5, qui de toute
 * façon interdit ce chemin pour `NEW_IPTV_MEDIA`/`MISSING_METADATA` ; `DETAIL_OPEN` sur une série
 * s'arrête donc au même niveau que les priorités de fond pour l'instant.
 */
class ExternalMetadataHydrationWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface HydrationWorkerEntryPoint {
        fun externalMetadataDao(): ExternalMetadataDao
        fun vodDao(): VodDao
        fun seriesDao(): SeriesDao
        fun externalMetadataRepository(): ExternalMetadataRepository
        fun externalMetadataBackfillSeeder(): ExternalMetadataBackfillSeeder
        fun timeProvider(): TimeProvider
    }

    override suspend fun doWork(): Result = try {
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, HydrationWorkerEntryPoint::class.java)
        val dao = entryPoint.externalMetadataDao()
        val timeProvider = entryPoint.timeProvider()
        drainQueue(dao, entryPoint.vodDao(), entryPoint.seriesDao(), entryPoint.externalMetadataRepository()) { timeProvider.nowMillis() }
        // F45-R6 : une passe courte remplit le prochain créneau seulement après le drainage. La
        // convergence continue donc sans scan massif au démarrage ; les demandes déjà en file sont
        // exclues par le DAO et le scheduler ne fait qu'un réveil par lot.
        entryPoint.externalMetadataBackfillSeeder().seed()
        // F45-R2 : la file peut ne plus rien avoir de dû *maintenant* (batch plafonné vidé, ou
        // items restants tous en backoff) sans être vide pour autant. Sans réveil programmé ici,
        // ces demandes restaient orphelines en Room jusqu'au prochain enqueue sans rapport
        // (ouverture de fiche, sync) ou redémarrage de l'app — la convergence s'arrêtait.
        nextWakeupDelayMillis(dao, timeProvider.nowMillis())?.let { delayMillis -> enqueueDelayed(applicationContext, delayMillis) }
        Result.success()
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        IptvLog.e("F45", "Hydratation externe : erreur worker", exception)
        Result.retry()
    }

    internal data class HydrationSource(val title: String, val year: Int?, val linkKey: String?, val hints: ExternalMatchHints)

    companion object {
        private const val UNIQUE_WORK_NAME = "external_metadata_hydration"
        internal const val MAX_ITEMS_PER_RUN = 200

        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, build())
        }

        /**
         * Programme le prochain passage : immédiat (`delayMillis == 0`, ex-`enqueueContinuation`
         * quand `MAX_ITEMS_PER_RUN` n'a pas suffi à vider la file) ou différé jusqu'à l'échéance de
         * backoff la plus proche (F45-R2). `APPEND_OR_REPLACE` chaîne après le run courant plutôt
         * que de l'annuler.
         */
        internal fun enqueueDelayed(context: Context, delayMillis: Long) {
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, build(delayMillis))
        }

        /**
         * F45-R2 : délai jusqu'au prochain item de la file (dû immédiatement ou encore en backoff),
         * `null` si la file est vide — isolé de `doWork()` pour rester testable sans WorkManager.
         */
        internal suspend fun nextWakeupDelayMillis(dao: ExternalMetadataDao, now: Long): Long? =
            dao.earliestNextAttemptAt()?.let { earliest -> (earliest - now).coerceAtLeast(0L) }

        private fun build(delayMillis: Long = 0L) = OneTimeWorkRequestBuilder<ExternalMetadataHydrationWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()

        /**
         * Boucle isolée, testable sans WorkManager (même motif que `CatalogNormalizationWorker.drainPages`).
         * @return `true` si `MAX_ITEMS_PER_RUN` a été atteint (la file peut ne pas être vide).
         */
        internal suspend fun drainQueue(
            dao: ExternalMetadataDao,
            vodDao: VodDao,
            seriesDao: SeriesDao,
            repository: ExternalMetadataRepository,
            now: () -> Long,
        ): Boolean {
            var processed = 0
            while (processed < MAX_ITEMS_PER_RUN) {
                val request = dao.nextRequest(now()) ?: return false
                processOne(request, dao, vodDao, seriesDao, repository, now())
                processed++
            }
            return true
        }

        private suspend fun processOne(
            request: ExternalHydrationRequestEntity,
            dao: ExternalMetadataDao,
            vodDao: VodDao,
            seriesDao: SeriesDao,
            repository: ExternalMetadataRepository,
            now: Long,
        ) {
            try {
                val source = source(request, vodDao, seriesDao)
                if (source == null) {
                    // Le média a disparu du catalogue (désabonnement/resync) depuis la mise en file : plus rien à faire.
                    dao.deleteRequest(request.kind, request.providerId)
                    return
                }
                // F45-R5 : seul `DETAIL_OPEN` peut faire retomber un hit local sur le réseau — les
                // priorités de fond ne doivent jamais rafraîchir une donnée stale (§7.1/§7.5).
                val allowRefresh = request.priority == HydrationReason.DETAIL_OPEN.priority
                val match = repository.match(request.kind, request.providerId, source.title, source.year, source.linkKey, source.hints, allowRefresh)
                dao.deleteRequest(request.kind, request.providerId)
                // F45-R7 : la série de niveau média est traitée par tous les producteurs, mais les
                // saisons/épisodes sont strictement réservés à l'ouverture réelle de la fiche.
                // La boucle du repository est séquentielle et son échec reste non bloquant : le
                // prochain DETAIL_OPEN pourra reprendre les saisons manquantes/stale.
                if (request.kind == "series" && request.priority == HydrationReason.DETAIL_OPEN.priority && match != null) {
                    repository.hydrateSeriesSeasons(match.externalId)
                }
                // F45-R4 : un hit local renvoie désormais une vraie confidence (plus jamais null par
                // construction) — `fromNetwork` est le seul signal fiable qu'un travail réseau a eu
                // lieu ; sans lui, chaque hit local re-propagerait `linkKey` inutilement à chaque passage.
                if (match != null && match.fromNetwork) {
                    propagateLinkKey(request.kind, request.providerId, source, match, dao, vodDao, seriesDao, now)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                requeueWithBackoff(request, dao, now)
            }
        }

        /** §8.10 : un seul appel réseau pour le groupe — les variantes partageant `linkKey` héritent du même externalId. */
        private suspend fun propagateLinkKey(
            kind: String,
            providerId: Int,
            source: HydrationSource,
            match: ExternalMetadataMatch,
            dao: ExternalMetadataDao,
            vodDao: VodDao,
            seriesDao: SeriesDao,
            now: Long,
        ) {
            val linkKey = source.linkKey ?: return
            val siblingIds = if (kind == "movie") {
                vodDao.getStreamsByLinkKey(linkKey, source.year, Int.MAX_VALUE).map { it.streamId }
            } else {
                seriesDao.getStreamsByLinkKey(linkKey, source.year, Int.MAX_VALUE).map { it.seriesId }
            }.filter { it != providerId }
            siblingIds.forEach { siblingProviderId ->
                dao.upsertLink(
                    ExternalMediaLinkEntity(
                        kind = kind, providerId = siblingProviderId, externalId = match.externalId, linkKey = linkKey,
                        confidence = match.confidence, matchMethod = match.matchMethod, matchVersion = match.matchVersion,
                        matchedAt = now, lastMatchAttemptAt = now, retryAfter = null,
                    ),
                )
            }
        }

        private suspend fun requeueWithBackoff(request: ExternalHydrationRequestEntity, dao: ExternalMetadataDao, now: Long) {
            val attemptCount = request.attemptCount + 1
            dao.upsertRequest(request.copy(attemptCount = attemptCount, nextAttemptAt = now + backoffDelayMillis(attemptCount)))
        }

        /** 10min, 20, 40, 80, 160, 320, plafonné à 6h (360min) — cooldown après échec fournisseur (§6, paramètre ajustable). */
        internal fun backoffDelayMillis(attemptCount: Int): Long {
            val minutes = 10L * (1L shl minOf(attemptCount - 1, 6))
            return TimeUnit.MINUTES.toMillis(minOf(minutes, 360L))
        }

        private suspend fun source(request: ExternalHydrationRequestEntity, vodDao: VodDao, seriesDao: SeriesDao): HydrationSource? {
            return if (request.kind == "movie") {
                val row = vodDao.getStreamById(request.providerId) ?: return null
                HydrationSource(
                    title = row.cleanTitle.ifBlank { row.name },
                    year = row.releaseYear,
                    linkKey = row.linkKey.ifBlank { null },
                    hints = ExternalMatchHints(
                        director = firstName(row.director),
                        actors = parseNames(row.actors),
                        genres = GenreParser.parseGenres(row.genre),
                        runtimeMinutes = parseDurationMinutes(row.duration),
                    ),
                )
            } else {
                val row = seriesDao.getStreamById(request.providerId) ?: return null
                HydrationSource(
                    title = row.cleanTitle.ifBlank { row.name },
                    year = row.releaseYear,
                    linkKey = row.linkKey.ifBlank { null },
                    hints = ExternalMatchHints(
                        director = firstName(row.director),
                        actors = parseNames(row.actors),
                        genres = GenreParser.parseGenres(row.genre),
                    ),
                )
            }
        }

        internal fun firstName(raw: String?): String? =
            raw?.split(",", "/")?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }

        internal fun parseNames(raw: String?): List<String> {
            if (raw.isNullOrBlank()) return emptyList()
            return raw.split(",", "/").map { it.trim() }.filter { it.isNotBlank() }.take(10)
        }

        /** Format Xtream `duration` observé : `HH:MM:SS` (parfois `MM:SS`). Tolérant, jamais 0/négatif. */
        internal fun parseDurationMinutes(duration: String?): Int? {
            if (duration.isNullOrBlank()) return null
            val parts = duration.trim().split(":").map { it.toIntOrNull() }
            if (parts.any { it == null }) return null
            val minutes = when (parts.size) {
                3 -> parts[0]!! * 60 + parts[1]!! + if (parts[2]!! >= 30) 1 else 0
                2 -> parts[0]!!
                else -> return null
            }
            return minutes.takeIf { it > 0 }
        }
    }
}
