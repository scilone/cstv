package com.poc.iptvxtream.domain.repository

import com.poc.iptvxtream.domain.model.MediaType
import com.poc.iptvxtream.domain.model.TrackPreference

interface TrackPreferenceRepository {

    /** Préférence mémorisée pour ce média et le profil actif, ou null si aucune. */
    suspend fun getPreference(mediaType: MediaType, mediaId: Int): TrackPreference?

    suspend fun saveAudioLang(mediaType: MediaType, mediaId: Int, audioLang: String?)

    suspend fun saveSubtitleLang(mediaType: MediaType, mediaId: Int, subtitleLang: String?)
}
