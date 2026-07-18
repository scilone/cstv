package com.cstv.app.domain.repository

import com.cstv.app.domain.model.MediaType
import com.cstv.app.domain.model.TrackPreference

interface TrackPreferenceRepository {

    /** Préférence mémorisée pour ce média et le profil actif, ou null si aucune. */
    suspend fun getPreference(mediaType: MediaType, mediaId: Int): TrackPreference?

    suspend fun saveAudioLang(mediaType: MediaType, mediaId: Int, audioLang: String?)

    suspend fun saveSubtitleLang(mediaType: MediaType, mediaId: Int, subtitleLang: String?)
}
