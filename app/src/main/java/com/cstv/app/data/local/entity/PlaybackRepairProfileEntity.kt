package com.cstv.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Configuration de réparation gagnante (T23 §8.5) mémorisée par média, au niveau de l'appareil —
 * pas par profil (décision produit étape 2 : la cause d'un échec de décodage est matérielle,
 * propre au fichier, indépendante du profil qui regarde). Contrairement à [TrackPreferenceEntity],
 * `mediaUid` est la clé primaire directe : une seule ligne par média, jamais par (profil, média).
 */
@Entity(
    tableName = "playback_repair_profiles",
    foreignKeys = [ForeignKey(
        entity = MediaRefEntity::class,
        parentColumns = ["mediaUid"],
        childColumns = ["mediaUid"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("mediaUid", unique = true)]
)
data class PlaybackRepairProfileEntity(
    @PrimaryKey val mediaUid: Long,
    val decoderStrategy: String,
    val disabledTrackJson: String?,
    val preferredAudioJson: String?,
    val updatedAt: Long,
    val schemaVersion: Int = 1
)
