package com.cstv.app.domain.repository

import com.cstv.app.domain.model.ExternalCatalogLink

/**
 * Résolution/persistance des associations `externalId` <-> média local
 * (T24). Voir `ExternalCatalogLinkEntity` pour pourquoi cette table est
 * découplée de `vod_streams`/`series_streams`.
 */
interface ExternalCatalogLinkRepository {
    /** Résolution batch : une requête indexée, jamais un scan catalogue. */
    suspend fun findByExternalIds(externalIds: List<String>): List<ExternalCatalogLink>

    /** Persiste plusieurs associations en une fois (après un match réussi). */
    suspend fun persistAll(links: List<ExternalCatalogLink>)
}
