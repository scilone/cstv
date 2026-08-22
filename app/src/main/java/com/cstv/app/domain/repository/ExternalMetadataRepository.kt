package com.cstv.app.domain.repository

import com.cstv.app.domain.model.ExternalMatchHints
import com.cstv.app.domain.model.ExternalMetadataCoverage
import com.cstv.app.domain.model.ExternalMetadataMatchOutcome
import com.cstv.app.domain.model.ExternalMetadataMatchRequest
import kotlinx.coroutines.flow.Flow

interface ExternalMetadataRepository {
    /**
     * Résout (ou réutilise) l'identité CSTV d'un média IPTV. `linkKey` sert à propager le même
     * `externalId` aux variantes locales compatibles (§8.10) — appelé séparément par le worker
     * d'hydratation (Tâche 7), pas ici.
     *
     * F45-R5 : `allowRefresh` porte la distinction §7.5/§7.10 « `refreshAfter` n'est évalué qu'à
     * l'ouverture d'une fiche ». `false` (défaut) : un lien local déjà résolu est toujours resservi
     * tel quel, jamais de rafraîchissement — c'est le seul comportement correct pour les priorités
     * de fond (`NEW_IPTV_MEDIA`/`MISSING_METADATA`), qui ne doivent jamais mettre une donnée stale
     * en file (§7.1). `true` : un hit local dont `refreshAfter` est dépassé retombe sur le réseau
     * au lieu d'être court-circuité — réservé à `DETAIL_OPEN` et aux lectures interactives
     * (`ContentClassificationRepository`).
     */
    suspend fun match(
        kind: String,
        providerId: Int,
        title: String,
        year: Int?,
        linkKey: String?,
        hints: ExternalMatchHints = ExternalMatchHints(),
        allowRefresh: Boolean = false,
    ): ExternalMetadataMatchOutcome

    suspend fun matchBatch(requests: List<ExternalMetadataMatchRequest>): List<ExternalMetadataMatchOutcome>

    /** F45-R7 : uniquement après une demande `DETAIL_OPEN` d'une série, jamais pendant le backfill. */
    suspend fun hydrateSeriesSeasons(externalId: String)

    /**
     * F46 : état courant de la couverture de l'enrichissement du catalogue local (films/séries
     * uniquement). Lecture Room pure, aucun appel réseau, jamais de matching déclenché par
     * l'observation.
     */
    fun observeCoverage(): Flow<ExternalMetadataCoverage>
}
