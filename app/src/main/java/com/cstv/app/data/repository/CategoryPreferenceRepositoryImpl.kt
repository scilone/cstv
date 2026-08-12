package com.cstv.app.data.repository

import com.cstv.app.data.local.dao.CategoryPreferenceDao
import com.cstv.app.data.local.entity.CategoryPreferenceEntity
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
    private val sync: CloudSyncManager? = null
) : CategoryPreferenceRepository {

    private val _changes = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val changes: Flow<Unit> = _changes.asSharedFlow()

    override suspend fun getPreferences(type: CategoryType): Map<String, CategoryPreference> {
        return dao.getForProfile(type.value, profileManager.currentProfileId())
            .associate { it.categoryId to CategoryPreference(it.categoryId, it.hidden, it.sortOrder) }
    }

    override suspend fun setHidden(type: CategoryType, categoryId: String, hidden: Boolean) {
        val profileId = profileManager.currentProfileId()
        val existing = dao.get(categoryId, type.value, profileId)
        dao.upsert(
            CategoryPreferenceEntity(
                categoryId = categoryId,
                type = type.value,
                profileId = profileId,
                hidden = hidden,
                sortOrder = existing?.sortOrder
            )
        )
        _changes.tryEmit(Unit)
        sync?.markDirty(profileId, SyncNamespace.CATEGORY_PREFERENCES)
    }

    override suspend fun saveOrder(type: CategoryType, orderedCategoryIds: List<String>) {
        val profileId = profileManager.currentProfileId()
        val existing = dao.getForProfile(type.value, profileId).associateBy { it.categoryId }
        dao.upsertAll(
            orderedCategoryIds.mapIndexed { index, categoryId ->
                CategoryPreferenceEntity(
                    categoryId = categoryId,
                    type = type.value,
                    profileId = profileId,
                    hidden = existing[categoryId]?.hidden ?: false,
                    sortOrder = index
                )
            }
        )
        _changes.tryEmit(Unit)
        sync?.markDirty(profileId, SyncNamespace.CATEGORY_PREFERENCES)
    }
}
