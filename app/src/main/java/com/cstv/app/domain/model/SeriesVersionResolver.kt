package com.cstv.app.domain.model

import com.cstv.app.domain.repository.SeriesRepository
import com.cstv.app.domain.repository.SeriesVersionPreferenceRepository
import javax.inject.Inject

/**
 * F39 §8.2/§8.3 : versions d'une série jouables pour l'épisode en cours —
 * mêmes `linkKey`/année (T21) que la série ouverte, mais uniquement les
 * candidates qui contiennent réellement le couple saison/épisode courant
 * (§7.4, cas limite « série incomplète » de la fiche F39). Une série
 * candidate sans épisode équivalent en cache est simplement absente du
 * résultat ; aucun appel réseau (`get_series_info`) n'est jamais déclenché
 * ici.
 */
class SeriesVersionResolver @Inject constructor(
    private val seriesRepository: SeriesRepository,
    private val preferenceRepository: SeriesVersionPreferenceRepository,
    private val categoryPreferenceRepository: com.cstv.app.domain.repository.CategoryPreferenceRepository? = null
) {

    suspend fun resolve(linkKey: String, releaseYear: Int?, seasonNum: Int, episodeNum: Int): List<SeriesVersionCandidate> {
        if (linkKey.isBlank()) return emptyList()
        val allSeries = seriesRepository.getVersionsByLinkKey(linkKey, releaseYear)
        val hiddenCategories = try {
            categoryPreferenceRepository?.getPreferences(com.cstv.app.domain.model.CategoryType.SERIES)
                ?.filterValues { it.hidden }
                ?.keys ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
        val allowedSeries = if (hiddenCategories.isEmpty()) allSeries else allSeries.filter { it.categoryId !in hiddenCategories }
        return allowedSeries.mapNotNull { series ->
            seriesRepository.getEpisodeBySeasonEpisode(series.seriesId, seasonNum, episodeNum)
                ?.let { episode -> SeriesVersionCandidate(series, episode) }
        }
    }

    /**
     * F39 §8.3 : version à lire compte tenu de la préférence mémorisée — la
     * candidate préférée si elle est toujours valide, sinon la série ouverte
     * parmi les candidates. Une préférence pointant vers une série ou un
     * épisode qui n'existe plus est effacée paresseusement (jamais réécrite
     * automatiquement sur la série ouverte : seul un choix explicite dans le
     * sélecteur remémorise une préférence, §8.5). `null` si aucune candidate
     * valide, y compris la série ouverte elle-même (§7.4).
     */
    suspend fun resolvePreferred(
        linkKey: String,
        releaseYear: Int?,
        seasonNum: Int,
        episodeNum: Int,
        openedSeriesId: Int
    ): SeriesVersionCandidate? {
        val candidates = resolve(linkKey, releaseYear, seasonNum, episodeNum)
        if (candidates.isEmpty()) return null

        val preferredId = preferenceRepository.getPreferredSeriesId(linkKey)
        val preferred = preferredId?.let { id -> candidates.firstOrNull { it.series.seriesId == id } }
        if (preferred != null) return preferred
        if (preferredId != null) preferenceRepository.clearPreference(linkKey)

        return candidates.firstOrNull { it.series.seriesId == openedSeriesId } ?: candidates.first()
    }
}

/** Une version candidate résolue : la série ET l'épisode équivalent qu'elle contient réellement. */
data class SeriesVersionCandidate(val series: SeriesStream, val episode: SeriesEpisode)
