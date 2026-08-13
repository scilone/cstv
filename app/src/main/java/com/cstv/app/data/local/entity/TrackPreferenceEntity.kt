package com.cstv.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Préférence de langue audio / sous-titres mémorisée par média (Phase 29), spécifique au profil
 * actif (Phase 27). `subtitleLang` peut valoir "none" pour un choix explicite « pas de
 * sous-titres ». T20 : la cible est `mediaUid`, commune aux films et aux épisodes (`kind` sur
 * `media_refs` porte la distinction que portait autrefois `mediaType`).
 */
@Entity(
    tableName = "track_preferences",
    primaryKeys = ["profileId", "mediaUid"],
    foreignKeys = [
        ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = MediaRefEntity::class, parentColumns = ["mediaUid"], childColumns = ["mediaUid"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("mediaUid")],
)
data class TrackPreferenceEntity(
    val profileId: Int,
    val mediaUid: Long,
    val audioLang: String?,
    val subtitleLang: String?,
)
