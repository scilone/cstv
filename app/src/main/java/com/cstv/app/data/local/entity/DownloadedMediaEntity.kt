package com.cstv.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Média téléchargé pour lecture hors-ligne (Phase 61, feature #15).
 *
 * Téléchargements **globaux** (décision produit) : pas de `profileId`, un fichier physique est
 * partagé par tous les profils du device — cohérent avec le catalogue/cache Room partagé.
 *
 * T20 : `contentId` ("movie_123" / "episode_456"), qui sert aussi de clé de téléchargement media3
 * (`DownloadRequest.id`) et de `customCacheKey` du cache ExoPlayer, n'est plus stocké — il est
 * **dérivable** de `(kind, providerId)` via `MediaRef` joint sur `mediaUid`, cf.
 * `DownloadContentId.of`/`parse`. Titre, jaquette, extension et appartenance à une série viennent
 * du catalogue. `mediaUid` devient la clé primaire ; l'unicité `(kind, providerId)` par identité
 * garantit qu'un même média n'a jamais deux lignes de téléchargement.
 */
@Entity(
    tableName = "downloaded_media",
    foreignKeys = [
        ForeignKey(entity = MediaRefEntity::class, parentColumns = ["mediaUid"], childColumns = ["mediaUid"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("mediaUid", unique = true)],
)
data class DownloadedMediaEntity(
    @PrimaryKey val mediaUid: Long,
    val status: String,               // voir DownloadStatus
    val percent: Int = 0,             // 0..100
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val createdAt: Long,
)
