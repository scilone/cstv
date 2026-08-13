package com.cstv.app.data.repository

import com.cstv.app.data.local.dao.CategoryPreferenceDao
import com.cstv.app.data.local.dao.CategoryRefDao
import com.cstv.app.data.local.entity.CategoryPreferenceEntity
import com.cstv.app.data.local.storage.CurrentAccountKeyProvider
import com.cstv.app.data.local.storage.ProfileManager
import com.cstv.app.domain.model.CategoryPreference
import com.cstv.app.domain.model.CategoryType
import com.cstv.app.domain.repository.CategoryPreferenceRepository
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton
import com.cstv.app.domain.sync.CloudSyncManager
import com.cstv.app.domain.sync.SyncNamespace

@Singleton
class CategoryPreferenceRepositoryImpl @Inject constructor(
    private val dao: CategoryPreferenceDao,
    private val profileManager: ProfileManager,
    private val categoryRefDao: CategoryRefDao,
    private val accountKeyProvider: CurrentAccountKeyProvider,
    private val sync: CloudSyncManager? = null
) : CategoryPreferenceRepository {

    private val _changes = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val changes: Flow<Unit> = _changes.asSharedFlow()

    override suspend fun getPreferences(type: CategoryType): Map<String, CategoryPreference> {
        return dao.getForProfile(profileManager.currentProfileId(), accountKeyProvider.current(), type.value)
            .associate { it.categoryId to CategoryPreference(it.categoryId, it.hidden, it.sortOrder) }
    }

    override suspend fun setHidden(type: CategoryType, categoryId: String, hidden: Boolean) {
        val profileId = profileManager.currentProfileId()
        val accountKey = accountKeyProvider.current()
        val existing = dao.get(profileId, accountKey, type.value, categoryId)
        val catUid = categoryRefDao.resolve(accountKey, type.value, categoryId)
        dao.upsert(CategoryPreferenceEntity(profileId = profileId, catUid = catUid, hidden = hidden, sortOrder = existing?.sortOrder))
        _changes.tryEmit(Unit)
        sync?.markDirty(profileId, SyncNamespace.CATEGORY_PREFERENCES)
    }

    override suspend fun saveOrder(type: CategoryType, orderedCategoryIds: List<String>) {
        val profileId = profileManager.currentProfileId()
        val accountKey = accountKeyProvider.current()
        val existing = dao.getForProfile(profileId, accountKey, type.value).associateBy { it.categoryId }
        dao.upsertAll(
            orderedCategoryIds.mapIndexed { index, categoryId ->
                CategoryPreferenceEntity(
                    profileId = profileId,
                    catUid = categoryRefDao.resolve(accountKey, type.value, categoryId),
                    hidden = existing[categoryId]?.hidden ?: false,
                    sortOrder = index
                )
            }
        )
        _changes.tryEmit(Unit)
        sync?.markDirty(profileId, SyncNamespace.CATEGORY_PREFERENCES)
    }
}
