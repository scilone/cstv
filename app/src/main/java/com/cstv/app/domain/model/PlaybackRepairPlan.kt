package com.cstv.app.domain.model

import com.google.gson.Gson

/**
 * Stratégie de décodage appliquée par [com.cstv.app.presentation.player.core.PlaybackEngineController]
 * (T23 §8.1). `SOFTWARE_PREFERRED` ne force que le type de renderer identifié en erreur par
 * [PlaybackFailureType.DECODER] — pour une erreur vidéo la priorité logicielle n'est utilisée
 * qu'en dernier recours (B16 : image corrompue avec un décodeur vidéo FFmpeg préféré), pour une
 * erreur audio elle confirme le renderer NextLib déjà préféré par défaut sans toucher la vidéo.
 */
enum class DecoderStrategy { DEFAULT, SOFTWARE_PREFERRED }

/**
 * Type de piste au sens Media3 (`C.TRACK_TYPE_*`), restreint aux deux types pertinents pour la
 * réparation (T23 §7.3 : décodeur, piste fautive, autre piste audio).
 */
enum class TrackKind { AUDIO, VIDEO }

/**
 * Empreinte stable d'une piste, indépendante de l'index de `TrackGroup` (qui peut changer entre
 * deux ouvertures du lecteur, T23 §8.3). Sert à la fois à identifier la piste fautive à
 * désactiver et à la sérialisation JSON persistée (§8.5).
 */
data class TrackFingerprint(
    val trackKind: TrackKind,
    val language: String?,
    val mimeType: String?,
    val codecs: String?,
    val channelCount: Int?,
    val roleFlags: Int,
    val label: String?
) {
    fun toJson(gson: Gson): String = gson.toJson(this)

    companion object {
        fun fromJson(gson: Gson, json: String?): TrackFingerprint? =
            json?.let { runCatching { gson.fromJson(it, TrackFingerprint::class.java) }.getOrNull() }
    }
}

/**
 * Plan de réparation reconstruisant exactement une instance ExoPlayer (T23 §8.1, §8.7). `null`
 * sur [disabledTrack]/[preferredAudio] signifie « pas d'ajustement de piste à cette étape ».
 *
 * [softwarePreferredTrackKind] (review R2) : type de renderer sur lequel porte réellement
 * [DecoderStrategy.SOFTWARE_PREFERRED] — `null` quand [decoderStrategy] est [DecoderStrategy.DEFAULT],
 * ou quand la piste fautive n'a pas pu être identifiée (séquence à l'aveugle, §7.4). Sans ce champ,
 * une erreur audio forçait quand même la priorité logicielle vidéo (B16 : risque d'image corrompue
 * pour un ajustement qui ne concernait pas la vidéo).
 */
data class PlaybackRepairPlan(
    val decoderStrategy: DecoderStrategy,
    val softwarePreferredTrackKind: TrackKind? = null,
    val disabledTrack: TrackFingerprint? = null,
    val preferredAudio: TrackFingerprint? = null
) {
    companion object {
        val DEFAULT = PlaybackRepairPlan(decoderStrategy = DecoderStrategy.DEFAULT)
    }
}
