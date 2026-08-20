package com.cstv.app.data.repository

import com.cstv.app.data.local.dao.ExternalCatalogLinkDao
import com.cstv.app.data.local.entity.ExternalCatalogLinkEntity
import com.cstv.app.domain.model.ExternalCatalogLink
import com.cstv.app.domain.repository.ExternalCatalogLinkRepository
import javax.inject.Inject

class ExternalCatalogLinkRepositoryImpl @Inject constructor(
    private val dao: ExternalCatalogLinkDao
) : ExternalCatalogLinkRepository {

    override suspend fun findByExternalIds(externalIds: List<String>): List<ExternalCatalogLink> {
        if (externalIds.isEmpty()) return emptyList()
        return dao.findByExternalIds(externalIds).map {
            ExternalCatalogLink(kind = it.kind, providerId = it.providerId, externalId = it.externalId)
        }
    }

    override suspend fun persistAll(links: List<ExternalCatalogLink>) {
        if (links.isEmpty()) return
        val now = System.currentTimeMillis()
        dao.upsertAll(links.map {
            ExternalCatalogLinkEntity(kind = it.kind, providerId = it.providerId, externalId = it.externalId, updatedAt = now)
        })
    }
}
