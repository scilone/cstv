package com.cstv.app.domain.model

/**
 * Association `externalId` (backend CSTV, opaque — jamais parsé) <-> média
 * local, résolue et persistée par [ExternalCatalogMatcher] via
 * `ExternalCatalogLinkRepository` (T24). `kind` vaut "movie" ou "series".
 */
data class ExternalCatalogLink(
    val kind: String,
    val providerId: Int,
    val externalId: String
)
