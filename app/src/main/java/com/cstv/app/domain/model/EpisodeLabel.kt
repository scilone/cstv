package com.cstv.app.domain.model

import java.util.Locale

/**
 * Libellé saison/épisode, unique pour toute l'application : « S01 E03 ».
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
        return String.format(Locale.ROOT, "S%02d E%02d", seasonNum, episodeNum)
    }
}
