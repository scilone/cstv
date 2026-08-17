package com.cstv.app.data.repository

import com.cstv.app.data.local.dao.MediaRefDao
import com.cstv.app.data.local.dao.PlaybackRepairProfileDao
import com.cstv.app.data.local.entity.PlaybackRepairProfileEntity
import com.cstv.app.data.local.storage.CurrentAccountKeyProvider
import com.cstv.app.domain.model.DecoderStrategy
import com.cstv.app.domain.model.PlaybackRepairPlan
import com.cstv.app.domain.model.TrackFingerprint
import com.cstv.app.domain.model.TrackKind
import com.cstv.app.domain.repository.PlaybackRepairRepository
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackRepairRepositoryImpl @Inject constructor(
    private val dao: PlaybackRepairProfileDao,
    private val mediaRefDao: MediaRefDao,
    private val accountKeyProvider: CurrentAccountKeyProvider,
    private val gson: Gson,
) : PlaybackRepairRepository {

    override suspend fun getRepairPlan(kind: String, providerId: Int): PlaybackRepairPlan? {
        val mediaUid = mediaRefDao.findUid(accountKeyProvider.current(), kind, providerId) ?: return null
        val entity = dao.getByMediaUid(mediaUid) ?: return null
        val (strategy, trackKind) = decodeDecoderStrategy(entity.decoderStrategy)
        return PlaybackRepairPlan(
            decoderStrategy = strategy,
            softwarePreferredTrackKind = trackKind,
            disabledTrack = TrackFingerprint.fromJson(gson, entity.disabledTrackJson),
            preferredAudio = TrackFingerprint.fromJson(gson, entity.preferredAudioJson)
        )
    }

    override suspend fun saveRepairPlan(kind: String, providerId: Int, plan: PlaybackRepairPlan) {
        val mediaUid = mediaRefDao.resolve(accountKeyProvider.current(), kind, providerId)
        dao.upsert(
            PlaybackRepairProfileEntity(
                mediaUid = mediaUid,
                decoderStrategy = encodeDecoderStrategy(plan),
                disabledTrackJson = plan.disabledTrack?.toJson(gson),
                preferredAudioJson = plan.preferredAudio?.toJson(gson),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun clearRepairPlan(kind: String, providerId: Int) {
        val mediaUid = mediaRefDao.findUid(accountKeyProvider.current(), kind, providerId) ?: return
        dao.deleteByMediaUid(mediaUid)
    }

    /**
     * Encode [PlaybackRepairPlan.softwarePreferredTrackKind] (review R2) dans la colonne
     * `decoderStrategy` existante (`"SOFTWARE_PREFERRED:VIDEO"`) plutôt que d'ajouter une colonne —
     * pas de migration Room supplémentaire pour ce champ. Rétrocompatible : une valeur sans `:`
     * (schéma d'avant ce correctif) se décode avec `trackKind = null`.
     */
    private fun encodeDecoderStrategy(plan: PlaybackRepairPlan): String =
        plan.decoderStrategy.name + (plan.softwarePreferredTrackKind?.let { ":${it.name}" } ?: "")

    private fun decodeDecoderStrategy(raw: String): Pair<DecoderStrategy, TrackKind?> {
        val parts = raw.split(':', limit = 2)
        val strategy = runCatching { DecoderStrategy.valueOf(parts[0]) }.getOrDefault(DecoderStrategy.DEFAULT)
        val trackKind = parts.getOrNull(1)?.let { runCatching { TrackKind.valueOf(it) }.getOrNull() }
        return strategy to trackKind
    }
}
