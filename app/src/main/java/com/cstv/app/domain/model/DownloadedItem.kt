package com.cstv.app.domain.model

/** Statut d'un téléchargement, miroir des états `Download.STATE_*` de media3. */
enum class DownloadStatus {
    QUEUED,       // en file d'attente
    DOWNLOADING,  // en cours
    PAUSED,       // arrêté (réseau coupé / stoppé)
    COMPLETED,    // terminé, lisible hors-ligne
    FAILED,       // échec
    REMOVING;     // suppression en cours (transitoire)

    val isTerminalSuccess: Boolean get() = this == COMPLETED
    val isActive: Boolean get() = this == QUEUED || this == DOWNLOADING || this == PAUSED
}

/**
 * Média téléchargé (ou en cours) tel qu'exposé à l'UI. Un film = un item, un
 * épisode de série = un item (l'unité téléchargeable est l'épisode).
 */
data class DownloadedItem(
    val contentId: String,
    val type: String,               // "movie" | "episode"
    val streamId: Int,
    val seriesId: Int?,
    val seasonNum: Int?,
    val episodeNum: Int?,
    val title: String,
    val subtitle: String?,
    val coverUrl: String?,
    val containerExtension: String,
    val status: DownloadStatus,
    val percent: Int,
    val bytesDownloaded: Long,
    val totalBytes: Long
) {
    companion object {
        const val TYPE_MOVIE = "movie"
        const val TYPE_EPISODE = "episode"

        fun movieContentId(streamId: Int) = "movie_$streamId"
        fun episodeContentId(episodeId: Int) = "episode_$episodeId"
    }
}

/**
 * Données nécessaires pour démarrer un téléchargement. L'URL (avec identifiants)
 * n'est pas persistée : elle sert à créer la `DownloadRequest` media3 et est
 * reconstruite à la demande depuis les identifiants stockés (chiffrés).
 */
data class DownloadRequestData(
    val contentId: String,
    val url: String,
    val type: String,
    val streamId: Int,
    val seriesId: Int?,
    val seasonNum: Int?,
    val episodeNum: Int?,
    val title: String,
    val subtitle: String?,
    val coverUrl: String?,
    val containerExtension: String
)
