package com.poc.iptvxtream.data.repository

import com.poc.iptvxtream.data.local.dao.TrackPreferenceDao
import com.poc.iptvxtream.data.local.entity.TrackPreferenceEntity
import com.poc.iptvxtream.data.local.storage.ProfileManager
import com.poc.iptvxtream.domain.model.MediaType
import com.poc.iptvxtream.domain.model.TrackPreference
import com.poc.iptvxtream.domain.repository.TrackPreferenceRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackPreferenceRepositoryImpl @Inject constructor(
    private val dao: TrackPreferenceDao,
    private val profileManager: ProfileManager
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
    }
}
