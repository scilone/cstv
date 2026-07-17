package com.poc.iptvxtream.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Média téléchargé pour lecture hors-ligne (Phase 61, feature #15).
 *
 * Téléchargements **globaux** (décision produit) : pas de `profileId`, un
 * fichier physique est partagé par tous les profils du device — cohérent avec
 * le catalogue/cache Room partagé.
 *
 * `contentId` (clé stable, ex. "movie_123" / "episode_456") sert aussi de clé
 * de téléchargement media3 (`DownloadRequest.id`) et de `customCacheKey` du
 * cache ExoPlayer : la lecture hors-ligne retrouve le fichier indépendamment
 * de l'URL (qui contient les identifiants et peut changer). Cette table est la
 * **source de vérité unique** de l'UI ; elle est synchronisée depuis le
 * `DownloadManager` media3 (statut + progression) par `DownloadRepositoryImpl`.
 */
@Entity(tableName = "downloaded_media")
data class DownloadedMediaEntity(
    @PrimaryKey val contentId: String,
    val type: String,                 // "movie" | "episode"
    val streamId: Int,                // streamId du film, ou id de l'épisode (= id de lecture)
    val seriesId: Int? = null,        // renseigné pour les épisodes
    val seasonNum: Int? = null,
    val episodeNum: Int? = null,
    val title: String,                // titre film, ou "Série — SxEy" pour un épisode
    val subtitle: String? = null,     // nom d'épisode / info secondaire
    val coverUrl: String? = null,
    val containerExtension: String,   // extension du flux (mp4/mkv/ts) pour reconstruire l'URL
    val status: String,               // voir DownloadStatus
    val percent: Int = 0,             // 0..100
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val createdAt: Long
)
