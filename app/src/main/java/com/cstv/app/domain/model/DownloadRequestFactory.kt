package com.cstv.app.domain.model

/**
 * Construit les [DownloadRequestData] pour un film ou un épisode. Objet pur et
 * testable : le contentId stable (clé de cache/téléchargement) et le format des
 * métadonnées (titre "Série — SxEy", sous-titre = nom d'épisode) sont vérifiés
 * ici, indépendamment de media3.
 */
object DownloadRequestFactory {

    fun forMovie(
        details: VodDetails,
        baseUrl: String,
        username: String,
        password: String
    ): DownloadRequestData = DownloadRequestData(
        contentId = DownloadedItem.movieContentId(details.streamId),
        url = details.getPlayUrl(baseUrl, username, password),
        type = DownloadedItem.TYPE_MOVIE,
        streamId = details.streamId,
        seriesId = null,
        seasonNum = null,
        episodeNum = null,
        title = details.name,
        subtitle = null,
        coverUrl = details.coverBig,
        containerExtension = details.containerExtension
    )

    fun forEpisode(
        episode: SeriesEpisode,
        seriesId: Int,
        seriesTitle: String,
        cover: String?,
        baseUrl: String,
        username: String,
        password: String
    ): DownloadRequestData = DownloadRequestData(
        contentId = DownloadedItem.episodeContentId(episode.id),
        url = episode.getPlayUrl(baseUrl, username, password),
        type = DownloadedItem.TYPE_EPISODE,
        streamId = episode.id,
        seriesId = seriesId,
        seasonNum = episode.seasonNum,
        episodeNum = episode.episodeNum,
        title = "$seriesTitle — S${episode.seasonNum}E${episode.episodeNum}",
        subtitle = episode.title,
        coverUrl = cover,
        containerExtension = episode.containerExtension
    )
}
