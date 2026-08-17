package com.cstv.app.domain.model

/**
 * Association `canonicalId` (backend CSTV, opaque — jamais parsé) <-> média
 * local, résolue et persistée par [TmdbCatalogMatcher] via
 * `CanonicalMediaLinkRepository` (T24). `kind` vaut "movie" ou "series".
 */
data class CanonicalMediaLink(
    val kind: String,
    val providerId: Int,
    val canonicalId: String
)
