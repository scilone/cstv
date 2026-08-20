package com.cstv.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * F45 (évolution F44) : autorisation permanente d'un média pour un profil
 * bridé, accordée explicitement lors d'un déverrouillage PIN (case « toujours
 * autoriser ce contenu sur ce profil »). Une fois posée, [CanPlayContentUseCase]
 * n'exige plus de PIN pour ce couple profil/média — pour une série, la clé
 * porte l'identité de la série entière, jamais d'un épisode (même règle que la
 * classification F44, §9 étape 1).
 */
@Entity(
    tableName = "parental_media_authorizations",
    primaryKeys = ["profileId", "mediaUid"],
    foreignKeys = [
        ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = MediaRefEntity::class, parentColumns = ["mediaUid"], childColumns = ["mediaUid"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("mediaUid")],
)
data class ParentalMediaAuthorizationEntity(
    val profileId: Int,
    val mediaUid: Long,
    val grantedAt: Long,
)
