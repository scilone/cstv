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
import com.cstv.app.domain.model.CatalogThrottledException
import com.cstv.app.domain.model.ExternalMatchHints
import com.cstv.app.domain.model.ExternalMetadataMatch
import com.cstv.app.domain.model.ExternalMetadataMatchOutcome
import com.cstv.app.domain.model.ExternalMetadataMatchRequest
import com.cstv.app.domain.model.GenreParser
import com.cstv.app.domain.model.HydrationReason
import com.cstv.app.domain.repository.ExternalMetadataRepository
import com.cstv.app.domain.util.TimeProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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
        val drain = drainQueue(dao, entryPoint.vodDao(), entryPoint.seriesDao(), entryPoint.externalMetadataRepository()) { timeProvider.nowMillis() }
        // F45-R6 : une passe courte remplit le prochain créneau seulement après le drainage. La
        // convergence continue donc sans scan massif au démarrage ; les demandes déjà en file sont
        // exclues par le DAO et le scheduler ne fait qu'un réveil par lot.
        entryPoint.externalMetadataBackfillSeeder().seed()
        // F45-R2 : la file peut ne plus rien avoir de dû *maintenant* (batch plafonné vidé, ou
        // items restants tous en backoff) sans être vide pour autant. Sans réveil programmé ici,
        // ces demandes restaient orphelines en Room jusqu'au prochain enqueue sans rapport
        // (ouverture de fiche, sync) ou redémarrage de l'app — la convergence s'arrêtait.
        // T29 débit §2 : après un throttle serveur, le réveil ne peut pas être plus tôt que le
        // `Retry-After` reçu — sinon le run suivant repart aussitôt sur les items *non* reprogrammés
        // et se fait refuser à nouveau, brûlant le quota au lieu de le respecter.
        nextWakeupDelayMillis(dao, timeProvider.nowMillis())
            ?.let { delayMillis -> maxOf(delayMillis, drain.throttleDelayMillis ?: 0L) }
            ?.let { delayMillis -> enqueueDelayed(applicationContext, delayMillis) }
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
        // T29 §8.11 : 20 -> 50, capacité maximale déjà acceptée par `/v1/catalog/matches/batch`
        // (§8.3) — réduit les allers-retours backend sans changer `MAX_ITEMS_PER_RUN` (isolé pour
        // mesurer le gain du seul batching avant de toucher à la durée d'un run WorkManager).
        internal const val MAX_ITEMS_PER_BATCH = 50

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
         * T29 débit §3 : intervalle minimum entre deux **démarrages** de requête réseau. Le quota
         * backend est de 30 requêtes/min/compte : une toutes les 2 s reste dessous sans jamais le
         * déclencher. Prévention seulement — plusieurs appareils peuvent partager le même compte, le
         * backend reste l'autorité finale (d'où le traitement du 429, §2).
         */
        internal const val MIN_BATCH_INTERVAL_MILLIS = 2_000L

        /** Throttle serveur sans `Retry-After` exploitable : délai de cadence court, jamais le backoff d'échec. */
        internal const val THROTTLE_FALLBACK_DELAY_MILLIS = 60_000L

        /**
         * @param runFull `true` si `MAX_ITEMS_PER_RUN` a été atteint (la file peut ne pas être vide).
         * @param throttleDelayMillis délai imposé par un HTTP 429 backend, à respecter avant tout
         *     nouveau réveil du worker — `null` quand aucun throttle n'a été rencontré.
         */
        internal data class DrainResult(val runFull: Boolean, val throttleDelayMillis: Long? = null)

        /**
         * Boucle isolée, testable sans WorkManager (même motif que `CatalogNormalizationWorker.drainPages`).
         */
        internal suspend fun drainQueue(
            dao: ExternalMetadataDao,
            vodDao: VodDao,
            seriesDao: SeriesDao,
            repository: ExternalMetadataRepository,
            now: () -> Long,
        ): DrainResult {
            var processed = 0
            // Instant du dernier START de requête réseau — `null` tant qu'aucune n'a été émise, pour
            // ne jamais retarder la toute première.
            var lastNetworkStartedAt: Long? = null
            while (processed < MAX_ITEMS_PER_RUN) {
                // Mockito returns null for an unstubbed Kotlin collection despite the DAO contract;
                // `orEmpty()` also makes a transient empty Room read explicit.
                val requests = dao.nextRequests(now(), minOf(MAX_ITEMS_PER_BATCH, MAX_ITEMS_PER_RUN - processed)).orEmpty()
                if (requests.isEmpty()) {
                    // Défense contre une ligne retirée entre la lecture groupée et l'exécution :
                    // le chemin unitaire conserve le traitement sans empêcher les lots normaux.
                    val request = dao.nextRequest(now()) ?: return DrainResult(false)
                    pace(lastNetworkStartedAt, now)
                    lastNetworkStartedAt = now()
                    processOne(request, dao, vodDao, seriesDao, repository, now())?.let { throttleDelay ->
                        return DrainResult(false, throttleDelay)
                    }
                    processed++
                    continue
                }
                val active = requests.mapNotNull { request ->
                    val source = source(request, vodDao, seriesDao)
                    if (source == null) {
                        dao.deleteRequest(request.kind, request.providerId)
                        null
                    } else {
                        request to source
                    }
                }
                if (active.isNotEmpty()) {
                    pace(lastNetworkStartedAt, now)
                    val startedAt = now()
                    try {
                        val matches = repository.matchBatch(active.map { (request, source) ->
                            ExternalMetadataMatchRequest(
                                kind = request.kind, providerId = request.providerId, title = source.title, year = source.year,
                                linkKey = source.linkKey, hints = source.hints,
                                allowRefresh = request.priority == HydrationReason.DETAIL_OPEN.priority,
                            )
                        })
                        check(matches.size == active.size) { "External metadata batch response size mismatch" }
                        // Un lot entièrement servi par les liens déjà présents en Room n'a consommé
                        // aucun quota : il ne doit pas imposer sa cadence au lot suivant.
                        if (matches.any { it !is ExternalMetadataMatchOutcome.Matched || it.match.fromNetwork }) {
                            lastNetworkStartedAt = startedAt
                        }
                        active.forEachIndexed { index, (request, source) ->
                            // T29 §7.6/§8.10 : seul `retry` (impossibilité technique temporaire) reste en
                            // file — `matched`/`unresolved` sont retirés comme avant, un `retry` ne doit
                            // jamais être persisté comme `unresolved` (§7.3).
                            when (val outcome = matches[index]) {
                                is ExternalMetadataMatchOutcome.Matched -> {
                                    dao.deleteRequest(request.kind, request.providerId)
                                    val match = outcome.match
                                    if (request.kind == "series" && request.priority == HydrationReason.DETAIL_OPEN.priority) {
                                        repository.hydrateSeriesSeasons(match.externalId)
                                    }
                                    if (match.fromNetwork) {
                                        propagateLinkKey(request.kind, request.providerId, source, match, dao, vodDao, seriesDao, now())
                                    }
                                }
                                ExternalMetadataMatchOutcome.Unresolved -> dao.deleteRequest(request.kind, request.providerId)
                                is ExternalMetadataMatchOutcome.Retry -> requeueWithBackoff(request, dao, now(), outcome.retryAfterMillis)
                            }
                        }
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (throttled: CatalogThrottledException) {
                        // T29 débit §2 : problème de **cadence**, pas d'échec. Les items restent en
                        // file, reprogrammés sur le `Retry-After` serveur, sans backoff 10 → 360 min et
                        // sans incrémenter `attemptCount` — sinon quelques throttles suffisaient à
                        // condamner ces médias à un backoff exponentiel de plusieurs heures.
                        val delayMillis = throttled.retryAfterMillis ?: THROTTLE_FALLBACK_DELAY_MILLIS
                        val throttledAt = now()
                        active.forEach { (request, _) -> requeueForThrottle(request, dao, throttledAt, delayMillis) }
                        return DrainResult(false, delayMillis)
                    } catch (exception: Exception) {
                        // Vraie erreur (réseau, réponse illisible) : comportement F45 inchangé.
                        lastNetworkStartedAt = startedAt
                        active.forEach { (request, _) -> requeueWithBackoff(request, dao, now()) }
                    }
                }
                processed += requests.size
            }
            return DrainResult(true)
        }

        /**
         * T29 débit §3 : borne l'intervalle entre deux **démarrages** de requête, jamais une pause
         * fixe ajoutée après coup — un batch de 4 s a déjà largement dépassé la cadence et repart
         * immédiatement, un batch de 0,2 s attend le complément (~1,8 s). `delay` (coroutine), jamais
         * `Thread.sleep` : le worker reste annulable et les tests avancent en temps virtuel.
         *
         * Le plafond sur l'attente protège d'un `now()` qui reculerait (heure murale ajustée par le
         * système) : au pire on attend un intervalle, jamais une durée absurde.
         */
        private suspend fun pace(lastNetworkStartedAt: Long?, now: () -> Long) {
            if (lastNetworkStartedAt == null) return
            val remaining = MIN_BATCH_INTERVAL_MILLIS - (now() - lastNetworkStartedAt)
            if (remaining > 0) delay(remaining.coerceAtMost(MIN_BATCH_INTERVAL_MILLIS))
        }

        /** @return le délai imposé par un throttle backend (HTTP 429), `null` dans tous les autres cas. */
        private suspend fun processOne(
            request: ExternalHydrationRequestEntity,
            dao: ExternalMetadataDao,
            vodDao: VodDao,
            seriesDao: SeriesDao,
            repository: ExternalMetadataRepository,
            now: Long,
        ): Long? {
            try {
                val source = source(request, vodDao, seriesDao)
                if (source == null) {
                    // Le média a disparu du catalogue (désabonnement/resync) depuis la mise en file : plus rien à faire.
                    dao.deleteRequest(request.kind, request.providerId)
                    return null
                }
                // F45-R5 : seul `DETAIL_OPEN` peut faire retomber un hit local sur le réseau — les
                // priorités de fond ne doivent jamais rafraîchir une donnée stale (§7.1/§7.5).
                val allowRefresh = request.priority == HydrationReason.DETAIL_OPEN.priority
                // T29 §7.6/§8.10 : un `retry` (impossibilité technique temporaire) reste en file —
                // il ne doit jamais être persisté comme `unresolved` (§7.3).
                when (val outcome = repository.match(request.kind, request.providerId, source.title, source.year, source.linkKey, source.hints, allowRefresh)) {
                    is ExternalMetadataMatchOutcome.Matched -> {
                        dao.deleteRequest(request.kind, request.providerId)
                        val match = outcome.match
                        // F45-R7 : la série de niveau média est traitée par tous les producteurs, mais les
                        // saisons/épisodes sont strictement réservés à l'ouverture réelle de la fiche.
                        // La boucle du repository est séquentielle et son échec reste non bloquant : le
                        // prochain DETAIL_OPEN pourra reprendre les saisons manquantes/stale.
                        if (request.kind == "series" && request.priority == HydrationReason.DETAIL_OPEN.priority) {
                            repository.hydrateSeriesSeasons(match.externalId)
                        }
                        // F45-R4 : un hit local renvoie désormais une vraie confidence (plus jamais null par
                        // construction) — `fromNetwork` est le seul signal fiable qu'un travail réseau a eu
                        // lieu ; sans lui, chaque hit local re-propagerait `linkKey` inutilement à chaque passage.
                        if (match.fromNetwork) {
                            propagateLinkKey(request.kind, request.providerId, source, match, dao, vodDao, seriesDao, now)
                        }
                    }
                    ExternalMetadataMatchOutcome.Unresolved -> dao.deleteRequest(request.kind, request.providerId)
                    is ExternalMetadataMatchOutcome.Retry -> requeueWithBackoff(request, dao, now, outcome.retryAfterMillis)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (throttled: CatalogThrottledException) {
                // T29 débit §2 : même règle que sur le chemin batch — cadence, pas échec.
                val delayMillis = throttled.retryAfterMillis ?: THROTTLE_FALLBACK_DELAY_MILLIS
                requeueForThrottle(request, dao, now, delayMillis)
                return delayMillis
            } catch (exception: Exception) {
                requeueWithBackoff(request, dao, now)
            }
            return null
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

        /**
         * §7.7 : `retryAfterMillis` (fourni par le backend, `Retry-After` TMDB) prime sur le backoff
         * exponentiel F45 quand connu — sinon le backoff habituel s'applique, comme pour toute erreur
         * technique classique (réseau, batch entier en échec).
         */
        private suspend fun requeueWithBackoff(request: ExternalHydrationRequestEntity, dao: ExternalMetadataDao, now: Long, retryAfterMillis: Long? = null) {
            val attemptCount = request.attemptCount + 1
            val delayMillis = retryAfterMillis ?: backoffDelayMillis(attemptCount)
            dao.upsertRequest(request.copy(attemptCount = attemptCount, nextAttemptAt = now + delayMillis))
        }

        /**
         * T29 débit §2 : reprogrammation après throttle serveur. `attemptCount` est délibérément
         * **inchangé** — un refus de cadence n'est pas une tentative ratée du média, et l'incrémenter
         * ferait basculer l'item sur le backoff exponentiel dès sa prochaine vraie erreur (voire
         * directement à plusieurs heures après quelques throttles). L'item n'est jamais retiré de la
         * file ni persisté comme `unresolved`.
         */
        private suspend fun requeueForThrottle(request: ExternalHydrationRequestEntity, dao: ExternalMetadataDao, now: Long, delayMillis: Long) {
            dao.upsertRequest(request.copy(nextAttemptAt = now + delayMillis))
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
