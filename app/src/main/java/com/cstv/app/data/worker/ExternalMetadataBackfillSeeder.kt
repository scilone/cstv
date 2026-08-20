package com.cstv.app.data.worker

import com.cstv.app.data.local.dao.BackfillCandidate
import com.cstv.app.data.local.dao.ExternalMetadataDao
import com.cstv.app.di.IptvLog
import com.cstv.app.domain.model.HydrationReason
import com.cstv.app.domain.util.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F45 §8.11 : rattrapage des films/séries sans état de résolution F45, par petits lots — couvre
 * aussi bien une première installation (tout est manquant) qu'une installation existante (le
 * catalogue d'avant F45 converge progressivement). Ne cherche que les données absentes : ignore
 * toute ligne déjà hydratée, même stale (aucun scan de `refreshAfter`, §7.1/§7.10).
 *
 * Ré-exécutable sans risque à chaque démarrage et à chaque fin de drainage : une fois qu'un lot est
 * en file, la page suivante ne le retrouve plus (exclu par `ExternalMetadataDao.find*`), donc les
 * passes progressent sans matérialiser tout le catalogue ni déclencher des milliers de réveils
 * WorkManager au démarrage (F45-R6).
 */
@Singleton
class ExternalMetadataBackfillSeeder @Inject constructor(
    private val dao: ExternalMetadataDao,
    private val scheduler: ExternalMetadataHydrationScheduler,
    private val timeProvider: TimeProvider,
) {
    suspend fun seed() {
        runCatching { seedKind("movie") { afterId, now, limit -> dao.findMoviesMissingExternalMetadata(afterId, now, limit) } }
            .onFailure { IptvLog.e("F45", "Backfill films impossible", it) }
        runCatching { seedKind("series") { afterId, now, limit -> dao.findSeriesMissingExternalMetadata(afterId, now, limit) } }
            .onFailure { IptvLog.e("F45", "Backfill séries impossible", it) }
    }

    private suspend fun seedKind(kind: String, page: suspend (afterId: Int, now: Long, limit: Int) -> List<BackfillCandidate>) {
        val candidates = page(0, timeProvider.nowMillis(), PAGE_SIZE)
        if (candidates.isEmpty()) return
        // §8.9/§8.10 : dédupliqué par linkKey — plusieurs variantes non résolues d'une même œuvre
        // ne déclenchent qu'une demande. Le lot est inséré transactionnellement et ne réveille
        // WorkManager qu'une fois (F45-R6).
        scheduler.requestBatch(
            kind,
            representativesByLinkKey(candidates).map(BackfillCandidate::providerId),
            HydrationReason.MISSING_METADATA,
        )
    }

    internal companion object {
        /** 100 films + 100 séries max par passe : petite empreinte sur les box peu puissantes. */
        const val PAGE_SIZE = 100

        fun representativesByLinkKey(candidates: List<BackfillCandidate>): List<BackfillCandidate> =
            candidates.groupBy { it.linkKey.ifBlank { "no-link:${it.providerId}" } }.map { (_, group) -> group.first() }
    }
}
