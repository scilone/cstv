package com.cstv.app.domain.usecase

import com.cstv.app.data.local.dao.VodDao
import com.cstv.app.data.local.entity.PlaybackPositionEntity
import com.cstv.app.data.local.storage.SettingsManager
import com.cstv.app.di.IptvLog
import com.cstv.app.domain.model.EpisodeRef
import com.cstv.app.domain.model.NewEpisodeDetector
import com.cstv.app.domain.model.PlaybackPosition
import com.cstv.app.domain.model.SeriesWatchState
import com.cstv.app.domain.notification.NewEpisodeNotifier
import com.cstv.app.domain.repository.ProfileRepository
import com.cstv.app.domain.repository.SeriesRepository
import com.cstv.app.domain.repository.SeriesWatchStateRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

private const val TAG = "NEWEP"

/**
 * Orchestration multi-profils de la détection de nouveaux épisodes (F12).
 * Post-traitement de la synchronisation catalogue, appelé après
 * [SyncCacheUseCase] et jamais depuis lui : un échec de détection ne doit
 * jamais transformer un succès de sync en retry (voir DatabaseSyncWorker).
 *
 * Accès direct aux DAO paramétrés par profil plutôt qu'aux repositories
 * scopés au profil actif (Favoris/Positions utilisent `flatMapLatest` sur le
 * profil actif, inadapté à un worker qui doit couvrir tous les profils).
 */
class DetectNewEpisodesUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val seriesWatchStateRepository: SeriesWatchStateRepository,
    private val vodDao: VodDao,
    private val seriesRepository: SeriesRepository,
    private val notifier: NewEpisodeNotifier,
    private val settingsManager: SettingsManager
) {
    suspend operator fun invoke() {
        val profiles = profileRepository.getProfiles()
        if (profiles.isEmpty()) return

        val startIndex = settingsManager.getNewEpisodesCheckCursor().mod(profiles.size)
        var budgetRemaining = MAX_SERIES_CHECKS_PER_RUN

        for (offset in profiles.indices) {
            if (budgetRemaining <= 0) break
            val profile = profiles[(startIndex + offset) % profiles.size]
            budgetRemaining -= processProfile(profile.id, budgetRemaining)
        }

        settingsManager.setNewEpisodesCheckCursor((startIndex + 1).mod(profiles.size))
    }

    /** @return le nombre de séries effectivement interrogées (appels réseau consommés). */
    private suspend fun processProfile(profileId: Int, budget: Int): Int {
        val eligible = seriesWatchStateRepository.eligibleSeriesIds(profileId)
        if (eligible.isEmpty()) return 0

        val states = seriesWatchStateRepository.getStates(profileId)
        val positionsBySeriesId = vodDao.getAllPlaybackPositions(profileId)
            .filter { it.seriesId != null }
            .groupBy { it.seriesId!! }

        // Pré-filtre sans réseau : baseline à établir, ou série réellement
        // terminée et candidate à une notification. Écarte toute série "en
        // cours de visionnage" avant tout appel réseau.
        val candidates = eligible.filter { seriesId ->
            val stored = states[seriesId] ?: return@filter true
            val completed = NewEpisodeDetector.latestCompleted(positionsFor(positionsBySeriesId, seriesId))
            completed != null && completed >= stored.lastKnown
        }

        if (candidates.isEmpty()) return 0

        // Favoris et historique récents d'abord.
        val sorted = candidates.sortedByDescending { seriesId ->
            positionsBySeriesId[seriesId]?.maxOfOrNull { it.lastAccessedAt } ?: 0L
        }

        // Curseur persistant par profil (F12-R1) : sans lui, un profil dont les
        // candidats dépassent le budget consomme systématiquement les mêmes
        // séries récentes à chaque exécution et n'atteint jamais les plus
        // anciennes. La rotation garantit qu'après assez d'exécutions toutes
        // les séries candidates finissent par être interrogées.
        val startIndex = settingsManager.getNewEpisodesSeriesCursor(profileId).mod(sorted.size)
        var consumed = 0
        for (offset in sorted.indices) {
            if (consumed >= budget) break
            val seriesId = sorted[(startIndex + offset) % sorted.size]
            consumed++
            checkSeries(profileId, seriesId, states[seriesId], positionsFor(positionsBySeriesId, seriesId))
        }
        settingsManager.setNewEpisodesSeriesCursor(profileId, (startIndex + consumed).mod(sorted.size))
        return consumed
    }

    private suspend fun checkSeries(
        profileId: Int,
        seriesId: Int,
        stored: SeriesWatchState?,
        positions: List<PlaybackPosition>
    ) {
        try {
            val details = seriesRepository.getSeriesDetails(seriesId)
            if (details.isMetadataIncomplete) return // Skip implicite : rien n'est écrit, réessai au prochain sync.

            val latestAvailable = details.episodes.values.flatten()
                .maxOfOrNull { EpisodeRef(it.seasonNum, it.episodeNum) }
            val completed = NewEpisodeDetector.latestCompleted(positions)

            when (val decision = NewEpisodeDetector.decide(stored, completed, latestAvailable)) {
                is NewEpisodeDetector.Decision.Notify -> {
                    // L'échec d'affichage (permission refusée, notifier
                    // indisponible) ne doit jamais empêcher la persistance de
                    // l'état : sinon la même série redéclencherait une
                    // tentative de notification à chaque synchronisation.
                    try {
                        notifier.notifyNewEpisodes(profileId, seriesId, details.name, decision.latestAvailable)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        IptvLog.e(TAG, "Notification impossible pour la série $seriesId", e)
                    }
                    seriesWatchStateRepository.upsert(
                        SeriesWatchState(profileId, seriesId, decision.latestAvailable, decision.latestAvailable)
                    )
                }
                is NewEpisodeDetector.Decision.Remember -> {
                    seriesWatchStateRepository.upsert(
                        SeriesWatchState(profileId, seriesId, decision.latestAvailable, stored?.lastNotified)
                    )
                }
                NewEpisodeDetector.Decision.Skip -> Unit
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Une série retirée du catalogue, un détail incomplet ou un timeout
            // n'interrompt pas le traitement des suivantes et ne modifie pas
            // l'état stocké.
            IptvLog.e(TAG, "Échec de vérification des nouveaux épisodes pour la série $seriesId", e)
        }
    }

    private fun positionsFor(byId: Map<Int, List<PlaybackPositionEntity>>, seriesId: Int): List<PlaybackPosition> =
        byId[seriesId].orEmpty().map {
            PlaybackPosition(
                streamId = it.streamId,
                positionMs = it.positionMs,
                durationMs = it.durationMs,
                lastAccessedAt = it.lastAccessedAt,
                seriesId = it.seriesId,
                episodeNum = it.episodeNum,
                seasonNum = it.seasonNum
            )
        }

    companion object {
        /** Global, pas par profil : le nombre de profils locaux est libre et ne doit pas multiplier la charge Xtream. */
        const val MAX_SERIES_CHECKS_PER_RUN = 20
    }
}
