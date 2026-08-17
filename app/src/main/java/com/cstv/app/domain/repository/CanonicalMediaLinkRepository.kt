package com.cstv.app.domain.repository

import com.cstv.app.domain.model.CanonicalMediaLink

/**
 * Résolution/persistance des associations `canonicalId` <-> média local
 * (T24). Voir `CanonicalMediaLinkEntity` pour pourquoi cette table est
 * découplée de `vod_streams`/`series_streams`.
 */
interface CanonicalMediaLinkRepository {
    /** Résolution batch : une requête indexée, jamais un scan catalogue. */
    suspend fun findByCanonicalIds(canonicalIds: List<String>): List<CanonicalMediaLink>

    /** Persiste plusieurs associations en une fois (après un match réussi). */
    suspend fun persistAll(links: List<CanonicalMediaLink>)
}
