package com.cstv.app.domain.model

import java.util.Locale

/**
 * Libellé saison/épisode, unique pour toute l'application : « S01E03 ».
 *
 * Avant, quatre variantes coexistaient selon l'écran (`S1 E3`, `S1E3`,
 * `S01E03`, `S01 E03`). Passer par cet objet évite qu'une nouvelle carte en
 * réinvente une cinquième.
 *
 * Objet pur et testable — aucune dépendance Android.
 */
object EpisodeLabel {

    /** `null` si la saison ou l'épisode est inconnu : mieux vaut rien afficher qu'un « S00 E00 ». */
    fun format(seasonNum: Int?, episodeNum: Int?): String? {
        if (seasonNum == null || episodeNum == null) return null
        return String.format(Locale.ROOT, "S%02dE%02d", seasonNum, episodeNum)
    }

    /**
     * Table clé → libellé pour le badge « S01E03 » de la rangée « Reprendre »
     * (B18) : une entrée par [PlaybackPosition] dont la saison et l'épisode
     * sont connus, sous la clé fournie par [keyOf] (`streamId` pour les films,
     * `seriesId ?: streamId` pour les séries). Une position de film — ou une
     * position de série aux métadonnées incomplètes — n'ajoute aucune entrée :
     * c'est ce qui garantit qu'aucun badge n'apparaît hors de propos.
     */
    fun buildResumeLabels(
        positions: List<PlaybackPosition>,
        keyOf: (PlaybackPosition) -> Int
    ): Map<Int, String> = positions.mapNotNull { position ->
        format(position.seasonNum, position.episodeNum)?.let { keyOf(position) to it }
    }.toMap()
}
