package com.cstv.app.data.worker

import com.cstv.app.data.local.dao.BackfillCandidate
import com.cstv.app.data.local.dao.ExternalMetadataDao
import com.cstv.app.di.IptvLog
import com.cstv.app.domain.model.HydrationReason
import com.cstv.app.domain.util.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F45 §8.11 : rattrapage des films/séries sans état de résolution F45 — couvre aussi bien une
 * première installation (tout est manquant) qu'une installation existante (le catalogue d'avant F45
 * converge progressivement). Ne cherche que les données absentes : ignore toute ligne déjà hydratée,
 * même stale (aucun scan de `refreshAfter`, §7.1/§7.10).
 *
 * T29 cycle backfill P0-3 : une seule page `afterId = 0` par kind ne suffisait plus. `drainQueue`
 * consomme jusqu'à 200 items par run alors que le seeder n'en proposait au mieux que 100 films +
 * 100 séries, dont une large part disparaissait à la déduplication par `linkKey` — la file tombait
 * à vide alors que des milliers de médias jamais traités attendaient plus loin dans le catalogue.
 * Le seeder pagine désormais réellement (keyset sur `afterId`) jusqu'à ramener la profondeur de
 * travail *prêt* à [TARGET_READY_DEPTH], puis s'arrête. Il ne rescanne donc pas tout le catalogue à
 * chaque réveil : dès que la file est assez fournie, aucune page n'est lue.
 *
 * Ré-exécutable sans risque à chaque démarrage et à chaque fin de drainage : les exclusions vivent
 * dans le SQL (déjà lié, cooldown `unresolved` non expiré, déjà en file), donc une ligne mise en
 * file par la page N n'est jamais reproposée par la page N+1.
 */
@Singleton
class ExternalMetadataBackfillSeeder @Inject constructor(
    private val dao: ExternalMetadataDao,
    private val scheduler: ExternalMetadataHydrationScheduler,
    private val timeProvider: TimeProvider,
) {
    /**
     * @param seeded nombre de demandes réellement ajoutées à la file lors de cette passe.
     * @param catalogExhausted `true` seulement si **chaque** kind a atteint la fin de son catalogue
     *     sans plus rien trouver à seeder. C'est le signal de convergence utilisé par le worker pour
     *     décider s'il doit garder un réveil de contrôle court ou se laisser endormir (P0-2) : une
     *     passe interrompue par la profondeur cible, par le plafond de pages ou par une erreur
     *     renvoie `false`, car du travail neuf peut encore exister.
     */
    data class SeedOutcome(val seeded: Int, val catalogExhausted: Boolean)

    suspend fun seed(): SeedOutcome {
        val now = timeProvider.nowMillis()
        val readyDepth = runCatching { dao.countReadyRequests(now) }
            .onFailure { IptvLog.e("F45", "Profondeur de file illisible", it) }
            .getOrDefault(0)

        val cursors = KINDS.associateWith { 0 }.toMutableMap()
        val finished = mutableSetOf<String>() // fin de catalogue atteinte
        val stopped = mutableSetOf<String>() // erreur : on ne conclut jamais à la convergence
        var seeded = 0
        var pages = 0

        while (readyDepth + seeded < TARGET_READY_DEPTH && (finished + stopped).size < KINDS.size && pages < MAX_PAGES_PER_SEED) {
            // Alternance stricte films/séries : un kind volumineux ne peut jamais consommer seul le
            // budget de pages et affamer l'autre.
            for (kind in KINDS) {
                if (kind in finished || kind in stopped) continue
                if (readyDepth + seeded >= TARGET_READY_DEPTH || pages >= MAX_PAGES_PER_SEED) break

                val page = runCatching { page(kind, cursors.getValue(kind), now) }
                    .onFailure {
                        IptvLog.e("F45", "Backfill $kind impossible", it)
                        stopped += kind
                    }
                    .getOrNull() ?: continue
                pages++
                if (page.isEmpty()) {
                    finished += kind
                    continue
                }
                // Keyset : la page suivante reprend strictement après le dernier candidat lu. Les
                // requêtes DAO trient par identifiant croissant, donc le curseur progresse toujours,
                // même si toute la page est absorbée par la déduplication ci-dessous.
                cursors[kind] = page.last().providerId
                // §8.9/§8.10 : dédupliqué par linkKey — plusieurs variantes non résolues d'une même
                // œuvre ne déclenchent qu'une demande. Le lot est inséré transactionnellement et ne
                // réveille WorkManager qu'une fois (F45-R6).
                val representatives = representativesByLinkKey(page).map(BackfillCandidate::providerId)
                if (representatives.isNotEmpty()) {
                    scheduler.requestBatch(kind, representatives, HydrationReason.MISSING_METADATA)
                    seeded += representatives.size
                }
            }
        }

        return SeedOutcome(seeded = seeded, catalogExhausted = finished.size == KINDS.size)
    }

    private suspend fun page(kind: String, afterId: Int, now: Long): List<BackfillCandidate> = when (kind) {
        MOVIE -> dao.findMoviesMissingExternalMetadata(afterId, now, PAGE_SIZE)
        else -> dao.findSeriesMissingExternalMetadata(afterId, now, PAGE_SIZE)
    }.orEmpty()

    internal companion object {
        const val MOVIE = "movie"
        const val SERIES = "series"
        val KINDS = listOf(MOVIE, SERIES)

        /** 100 candidats par lecture : petite empreinte mémoire sur les box peu puissantes. */
        const val PAGE_SIZE = 100

        /**
         * Profondeur de travail *prêt* visée, exprimée en demandes immédiatement traitables. Deux
         * runs pleins (`MAX_ITEMS_PER_RUN = 200`) : le worker garde de quoi enchaîner un run complet
         * même si la passe de seeding suivante échoue ou n'a rien trouvé.
         */
        const val TARGET_READY_DEPTH = ExternalMetadataHydrationWorker.MAX_ITEMS_PER_RUN * 2

        /**
         * Plafond de lectures par passe, tous kinds confondus : borne dure contre le scan complet du
         * catalogue à chaque réveil quand la déduplication par `linkKey` réduit fortement chaque page.
         * Atteindre ce plafond n'est pas une convergence — la passe suivante reprendra.
         */
        const val MAX_PAGES_PER_SEED = 30

        fun representativesByLinkKey(candidates: List<BackfillCandidate>): List<BackfillCandidate> =
            candidates.groupBy { it.linkKey.ifBlank { "no-link:${it.providerId}" } }.map { (_, group) -> group.first() }
    }
}
