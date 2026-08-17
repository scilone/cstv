package com.cstv.app.data.repository

import com.cstv.app.data.local.dao.SeriesVersionPreferenceDao
import com.cstv.app.data.local.entity.SeriesVersionPreferenceEntity
import com.cstv.app.data.local.storage.ProfileManager
import com.cstv.app.domain.repository.SeriesVersionPreferenceRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeriesVersionPreferenceRepositoryImpl @Inject constructor(
    private val dao: SeriesVersionPreferenceDao,
    private val profileManager: ProfileManager
) : SeriesVersionPreferenceRepository {

    override suspend fun getPreferredSeriesId(linkKey: String): Int? =
        dao.getPreferredSeriesId(profileManager.currentProfileId(), linkKey)

    override suspend fun setPreference(linkKey: String, preferredSeriesId: Int) {
        dao.upsert(
            SeriesVersionPreferenceEntity(
                profileId = profileManager.currentProfileId(),
                linkKey = linkKey,
                preferredSeriesId = preferredSeriesId,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun clearPreference(linkKey: String) {
        dao.delete(profileManager.currentProfileId(), linkKey)
    }
}
