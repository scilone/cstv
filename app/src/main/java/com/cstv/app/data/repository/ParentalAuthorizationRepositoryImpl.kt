package com.cstv.app.data.repository

import com.cstv.app.data.local.dao.MediaRefDao
import com.cstv.app.data.local.dao.ParentalAuthorizationDao
import com.cstv.app.data.local.entity.ParentalMediaAuthorizationEntity
import com.cstv.app.data.local.storage.CurrentAccountKeyProvider
import com.cstv.app.domain.repository.ParentalAuthorizationRepository
import com.cstv.app.domain.sync.CloudSyncManager
import com.cstv.app.domain.sync.SyncNamespace
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ParentalAuthorizationRepositoryImpl @Inject constructor(
    private val dao: ParentalAuthorizationDao,
    private val mediaRefDao: MediaRefDao,
    private val accountKeyProvider: CurrentAccountKeyProvider,
    private val sync: CloudSyncManager? = null,
) : ParentalAuthorizationRepository {

    override suspend fun isAuthorized(profileId: Int, kind: MediaClassificationKind, providerId: Int): Boolean =
        dao.isAuthorized(profileId, accountKeyProvider.current(), kind.wireValue, providerId)

    override suspend fun authorize(profileId: Int, kind: MediaClassificationKind, providerId: Int) {
        val accountKey = accountKeyProvider.current()
        val mediaUid = mediaRefDao.resolve(accountKey, kind.wireValue, providerId)
        dao.upsert(ParentalMediaAuthorizationEntity(profileId = profileId, mediaUid = mediaUid, grantedAt = System.currentTimeMillis()))
        sync?.markDirty(profileId, SyncNamespace.PARENTAL_AUTHORIZATIONS)
    }
}
