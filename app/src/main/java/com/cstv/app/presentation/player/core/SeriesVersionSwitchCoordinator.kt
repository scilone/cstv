package com.cstv.app.presentation.player.core

import com.cstv.app.domain.model.SeriesEpisode
import com.cstv.app.domain.model.SeriesVersionCandidate

/**
 * F39-R2/R3/R4 (étape 7) : orchestration pure de « quelle version série est réellement jouée »,
 * extraite de `SeriesPlayerScreen` pour rester testable en JVM sans Compose (AGENTS.md, aucun
 * device requis) — la review notait qu'aucun scénario automatisé ne pouvait prouver l'application
 * de la préférence mémorisée ni la cohérence d'une séquence A → B → C de bascules.
 *
 * Porte l'identité de version jouable courante ([currentSeriesId]/[currentEpisode]) comme un seul
 * état atomique : les deux changent toujours ensemble, jamais l'un sans l'autre (F39-R3). Toute
 * cible `previous`/`next` de bascule est construite depuis cet état, jamais depuis une valeur
 * capturée séparément côté appelant qui pourrait diverger.
 */
class SeriesVersionSwitchCoordinator(
    initialSeriesId: Int,
    initialEpisode: SeriesEpisode,
    private val versionsEnabled: Boolean,
    private val switchController: MediaVersionSwitchController,
    private val buildPlayUrl: (SeriesEpisode) -> String,
    private val buildCacheKey: (SeriesEpisode) -> String,
    /** F39-R2 : résolution locale uniquement (§8.2) — voir `SeriesVersionResolver.resolvePreferred`. */
    private val resolvePreferred: suspend (openSeriesId: Int, seasonNum: Int, episodeNum: Int) -> SeriesVersionCandidate?,
    private val onPersistPreference: suspend (linkKey: String, seriesId: Int) -> Unit
) {
    var currentSeriesId: Int = initialSeriesId
        private set
    var currentEpisode: SeriesEpisode = initialEpisode
        private set

    /**
     * F39-R2 : résout et applique la version à jouer pour ([seasonNum], [episodeNum]) — ouverture
     * initiale de l'écran ou enchaînement (binge) vers un nouvel épisode, [fallback] étant
     * l'épisode que l'appelant jouerait sans préférence (l'épisode navigué, ou celui calculé par
     * `computeNextEpisode`). Ne déclenche strictement aucune résolution en mode hors ligne
     * (`versionsEnabled=false`) : [fallback] est appliqué tel quel, comme avant F39.
     */
    suspend fun resolveAndSet(seasonNum: Int, episodeNum: Int, fallback: SeriesEpisode) {
        if (!versionsEnabled) {
            currentEpisode = fallback
            return
        }
        val preferred = resolvePreferred(currentSeriesId, seasonNum, episodeNum)
        if (preferred != null) {
            currentSeriesId = preferred.series.seriesId
            currentEpisode = preferred.episode
        } else {
            currentEpisode = fallback
        }
    }

    /** Issue de [switchTo] côté coordinator — ce que l'appelant Compose doit répercuter sur l'UI. */
    sealed class SwitchOutcome {
        /** Bascule réussie : [abandonedEpisode] est l'épisode quitté (pour sa « reprise », §8.5 pt.6). */
        data class Applied(val abandonedEpisode: SeriesEpisode) : SwitchOutcome()
        data object RolledBack : SwitchOutcome()
        data object Superseded : SwitchOutcome()
    }

    /**
     * F39-R3 : construit `previous` depuis [currentEpisode] — jamais depuis une capture externe —
     * ce qui garantit qu'une séquence A → B (réussie) → C (échec) restaure bien B, pas A : après
     * le succès A → B, [currentEpisode] vaut déjà B avant que l'appelant ne déclenche B → C.
     */
    suspend fun switchTo(candidate: SeriesVersionCandidate): SwitchOutcome {
        val abandonedEpisode = currentEpisode
        val previousTarget = PlayableVersionTarget(
            mediaUrl = buildPlayUrl(abandonedEpisode),
            cacheKey = buildCacheKey(abandonedEpisode)
        )
        val nextTarget = PlayableVersionTarget(
            mediaUrl = buildPlayUrl(candidate.episode),
            cacheKey = buildCacheKey(candidate.episode)
        )
        return when (switchController.switchTo(previousTarget, nextTarget)) {
            MediaVersionSwitchResult.Switched -> {
                currentSeriesId = candidate.series.seriesId
                currentEpisode = candidate.episode
                candidate.series.linkKey.takeIf { it.isNotBlank() }?.let { linkKey ->
                    onPersistPreference(linkKey, candidate.series.seriesId)
                }
                SwitchOutcome.Applied(abandonedEpisode)
            }
            MediaVersionSwitchResult.RolledBack, MediaVersionSwitchResult.RollbackFailed -> SwitchOutcome.RolledBack
            MediaVersionSwitchResult.Superseded -> SwitchOutcome.Superseded
        }
    }
}
