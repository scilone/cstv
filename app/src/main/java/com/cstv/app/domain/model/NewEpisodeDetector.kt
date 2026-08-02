package com.cstv.app.domain.model

/**
 * Décision pure de notification de nouveaux épisodes (F12). Objet sans
 * dépendance Android ni Room : les erreurs de cette fonctionnalité sont
 * silencieuses par nature (notification manquante ou en double, invisible en
 * debug) — la seule couverture réaliste est une logique 100% testable en JVM.
 */
object NewEpisodeDetector {

    const val COMPLETION_THRESHOLD_MS = 15_000L

    sealed interface Decision {
        /** Premier passage ou rien à signaler : on mémorise l'état du catalogue sans alerter. */
        data class Remember(val latestAvailable: EpisodeRef) : Decision
        /** Nouveaux épisodes sur une série terminée et non encore notifiée. */
        data class Notify(val latestAvailable: EpisodeRef) : Decision
        /** Détail inexploitable : ne rien écrire, réessai au prochain sync. */
        data object Skip : Decision
    }

    /** Dernier épisode réellement terminé par le profil, ou null si aucun. */
    fun latestCompleted(positions: List<PlaybackPosition>): EpisodeRef? = positions
        .filter { it.seasonNum != null && it.episodeNum != null }
        .filter { it.durationMs > 0L && it.positionMs >= it.durationMs - COMPLETION_THRESHOLD_MS }
        .maxOfOrNull { EpisodeRef(it.seasonNum!!, it.episodeNum!!) }

    fun decide(
        stored: SeriesWatchState?,
        latestCompleted: EpisodeRef?,
        latestAvailable: EpisodeRef?
    ): Decision {
        if (latestAvailable == null) return Decision.Skip
        val lastKnown = stored?.lastKnown ?: return Decision.Remember(latestAvailable)
        val notify = latestCompleted != null &&
            latestCompleted >= lastKnown &&
            latestAvailable > latestCompleted &&
            latestAvailable > (stored.lastNotified ?: EpisodeRef(-1, -1))
        return if (notify) Decision.Notify(latestAvailable) else Decision.Remember(latestAvailable)
    }
}
