package com.cstv.app.data.repository

import com.cstv.app.data.local.dao.TrackPreferenceDao
import com.cstv.app.data.local.entity.TrackPreferenceEntity
import com.cstv.app.data.local.storage.ProfileManager
import com.cstv.app.domain.model.MediaType
import com.cstv.app.domain.model.TrackPreference
import com.cstv.app.domain.repository.TrackPreferenceRepository
import com.cstv.app.domain.sync.CloudSyncManager
import com.cstv.app.domain.sync.SyncNamespace
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackPreferenceRepositoryImpl @Inject constructor(
    private val dao: TrackPreferenceDao,
    private val profileManager: ProfileManager,
    private val sync: CloudSyncManager? = null
) : TrackPreferenceRepository {

    override suspend fun getPreference(mediaType: MediaType, mediaId: Int): TrackPreference? {
        val entity = dao.getPreference(profileManager.currentProfileId(), mediaType.value, mediaId)
            ?: return null
        return TrackPreference(entity.audioLang, entity.subtitleLang)
    }

    override suspend fun saveAudioLang(mediaType: MediaType, mediaId: Int, audioLang: String?) {
        val profileId = profileManager.currentProfileId()
        val existing = dao.getPreference(profileId, mediaType.value, mediaId)
        dao.upsert(
            TrackPreferenceEntity(
                profileId = profileId,
                mediaType = mediaType.value,
                mediaId = mediaId,
                audioLang = audioLang,
                subtitleLang = existing?.subtitleLang
            )
        )
        sync?.markDirty(profileId, SyncNamespace.TRACK_PREFERENCES)
    }

    override suspend fun saveSubtitleLang(mediaType: MediaType, mediaId: Int, subtitleLang: String?) {
        val profileId = profileManager.currentProfileId()
        val existing = dao.getPreference(profileId, mediaType.value, mediaId)
        dao.upsert(
            TrackPreferenceEntity(
                profileId = profileId,
                mediaType = mediaType.value,
                mediaId = mediaId,
                audioLang = existing?.audioLang,
                subtitleLang = subtitleLang
            )
        )
        sync?.markDirty(profileId, SyncNamespace.TRACK_PREFERENCES)
    }
}
