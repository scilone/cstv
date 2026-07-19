package com.cstv.app.presentation.player.cast

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

/**
 * Construit le [MediaItem] Media3 envoyé à l'appareil Chromecast à partir d'une
 * URL de lecture Xtream et de ses métadonnées.
 *
 * Le récepteur par défaut Cast a besoin d'un **type MIME explicite** (il ne
 * devine pas depuis l'extension). La sélection du MIME est une fonction pure et
 * testable ([mimeTypeFor]). L'URL contient les identifiants Xtream : ne jamais
 * la logger.
 */
object CastMediaItemFactory {

    /**
     * Déduit le type MIME Cast depuis l'extension du flux :
     * - `m3u8` / `ts` → HLS (`application/x-mpegurl`) — flux live et VOD HLS ;
     * - `mp4` → `video/mp4` ;
     * - `mkv` → `video/x-matroska` (non garanti sur le récepteur par défaut) ;
     * - inconnu / vide → repli `video/mp4`.
     * Insensible à la casse et au point de préfixe éventuel.
     */
    fun mimeTypeFor(extension: String?): String {
        val ext = extension?.trim()?.lowercase()?.removePrefix(".").orEmpty()
        return when (ext) {
            "m3u8", "ts", "hls" -> "application/x-mpegurl"
            "mp4", "m4v", "mov" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            else -> "video/mp4"
        }
    }

    /**
     * @param url URL de lecture (contient les credentials — ne pas logger).
     * @param title Titre affiché sur l'appareil Cast.
     * @param extension Extension du flux (pour le MIME).
     * @param artworkUrl Poster/cover éventuel affiché par le récepteur.
     */
    fun build(
        url: String,
        title: String?,
        extension: String?,
        artworkUrl: String? = null
    ): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(title.orEmpty())
        if (!artworkUrl.isNullOrBlank()) {
            metadataBuilder.setArtworkUri(Uri.parse(artworkUrl))
        }
        return MediaItem.Builder()
            .setUri(url)
            .setMimeType(mimeTypeFor(extension))
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }
}
