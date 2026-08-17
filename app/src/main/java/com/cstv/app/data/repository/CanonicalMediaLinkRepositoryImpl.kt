package com.cstv.app.data.repository

import com.cstv.app.data.local.dao.CanonicalMediaLinkDao
import com.cstv.app.data.local.entity.CanonicalMediaLinkEntity
import com.cstv.app.domain.model.CanonicalMediaLink
import com.cstv.app.domain.repository.CanonicalMediaLinkRepository
import javax.inject.Inject

class CanonicalMediaLinkRepositoryImpl @Inject constructor(
    private val dao: CanonicalMediaLinkDao
) : CanonicalMediaLinkRepository {

    override suspend fun findByCanonicalIds(canonicalIds: List<String>): List<CanonicalMediaLink> {
        if (canonicalIds.isEmpty()) return emptyList()
        return dao.findByCanonicalIds(canonicalIds).map {
            CanonicalMediaLink(kind = it.kind, providerId = it.providerId, canonicalId = it.canonicalId)
        }
    }

    override suspend fun persistAll(links: List<CanonicalMediaLink>) {
        if (links.isEmpty()) return
        val now = System.currentTimeMillis()
        dao.upsertAll(links.map {
            CanonicalMediaLinkEntity(kind = it.kind, providerId = it.providerId, canonicalId = it.canonicalId, updatedAt = now)
        })
    }
}
