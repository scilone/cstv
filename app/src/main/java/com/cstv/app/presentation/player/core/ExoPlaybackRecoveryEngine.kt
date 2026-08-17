package com.cstv.app.presentation.player.core

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.cstv.app.domain.model.DecoderStrategy
import com.cstv.app.domain.model.PlaybackRepairPlan
import com.cstv.app.domain.model.TrackFingerprint
import com.cstv.app.domain.model.TrackKind
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Adapter mince (T23 §8.1, §8.6) reliant [PlaybackEngineController] (tâche 4) à l'interface
 * [PlaybackRecoveryEngine] attendue par [PlaybackRecoveryCoordinator] (tâche 5) — non testable en
 * JVM sans device (AGENTS.md, §8.8 : « détails Media3 non exécutables en JVM contenus dans des
 * adapters minces et validés par compilation »), câblé en tâche 7.
 */
@UnstableApi
class ExoPlaybackRecoveryEngine(private val controller: PlaybackEngineController) : PlaybackRecoveryEngine {

    private var currentDecoderStrategy = DecoderStrategy.DEFAULT
    private var pendingInitialTrackPlan: PlaybackRepairPlan? = null

    override fun applyPlan(plan: PlaybackRepairPlan, restore: PlaybackRestoreState): Flow<PlaybackEngineEvent> = callbackFlow {
        val player = rebuildForStrategy(plan)
        applyTrackSelection(player, plan)

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) trySend(PlaybackEngineEvent.Ready)
            }

            override fun onPlayerError(error: PlaybackException) {
                // Review R9 : seul un vrai échec de décodage fait avancer la séquence de
                // réparation ; réseau/live-window/inconnu interrompt l'essai en cours sans être
                // traité comme un échec « appareil » (routé par le coordinateur en RecoveryOutcome.Aborted).
                val event = when (val type = PlaybackFailureClassifier.classify(error)) {
                    PlaybackFailureType.DECODER -> PlaybackEngineEvent.RendererFailure(PlaybackFailureClassifier.failedTrack(error))
                    else -> PlaybackEngineEvent.NonDecoderFailure(type)
                }
                trySend(event)
            }
        }
        player.addListener(listener)

        // `C.TIME_UNSET` (direct sans position de tampon F41, tâche 6) : ne pas seek du tout,
        // laisser le lecteur reprendre naturellement au live edge plutôt que de forcer un
        // `seekTo` vers le début de la fenêtre — un `seekTo(0)` sur un flux live ne reprend pas
        // au direct, il saute au début du buffer disponible.
        if (restore.positionMs != C.TIME_UNSET) player.seekTo(restore.positionMs)
        player.playbackParameters = player.playbackParameters.withSpeed(restore.speed)
        player.playWhenReady = restore.playWhenReady
        player.prepare()

        awaitClose { player.removeListener(listener) }
    }

    override suspend fun firstAlternateAudioTrack(excluding: TrackFingerprint?): TrackFingerprint? {
        val group = audioTracks(controller.player)
            .firstOrNull { (_, format) -> excluding == null || !formatMatches(format, excluding) }
            ?: return null
        return fingerprintOf(group.second)
    }

    /**
     * Prépare le lecteur pour l'ouverture d'un média avec [plan] — profil mémorisé ou
     * [PlaybackRepairPlan.DEFAULT], en dehors de tout essai de réparation (review R1, R4).
     * Reconstruit si la stratégie de décodeur diffère, puis nettoie *immédiatement* tout override
     * de piste résiduel du média précédent (R4). L'override du plan lui-même ne peut être posé
     * qu'une fois les pistes du nouveau média connues — impossible avant `onTracksChanged`, Media3
     * n'exposant les `TrackGroup` qu'après cet événement — il est donc mémorisé et appliqué par
     * [applyPendingInitialTrackSelection] (R1 : un profil gagnant aux étapes 2/3 était jusqu'ici
     * ignoré au rejeu, faute de ce second temps).
     */
    fun prepareInitialPlan(plan: PlaybackRepairPlan): ExoPlayer {
        val player = rebuildForStrategy(plan)
        clearRepairTrackOverrides(player)
        pendingInitialTrackPlan = plan.takeIf { it.disabledTrack != null || it.preferredAudio != null }
        return player
    }

    /** À appeler depuis `onTracksChanged` de l'écran une fois les pistes du média connues (R1). */
    fun applyPendingInitialTrackSelection() {
        val plan = pendingInitialTrackPlan ?: return
        pendingInitialTrackPlan = null
        applyTrackSelection(controller.player, plan)
    }

    private fun rebuildForStrategy(plan: PlaybackRepairPlan): ExoPlayer {
        // Un changement de stratégie décodeur exige une reconstruction complète (renderers
        // différents, §8.7 — une seule instance vivante à la fois) ; à stratégie égale, seule la
        // sélection de piste change sur la même instance, sans reconstruction inutile.
        return if (plan.decoderStrategy != currentDecoderStrategy) {
            currentDecoderStrategy = plan.decoderStrategy
            controller.rebuild(plan)
        } else {
            controller.player
        }
    }

    /**
     * Applique [plan] sur [player] (review R1, R2, R4) : commence par retirer tout override de
     * piste résiduel (essai précédent de la même séquence, ou média précédent) plutôt que de
     * construire par-dessus — sans ça, un plan sans ajustement de piste laissait filtrer la
     * sélection du plan précédent (R4). `preferredAudio` force ensuite la sélection de la piste
     * correspondante (même mécanisme que la préférence de langue manuelle déjà en place côté
     * écrans) ; `disabledTrack` exclut sa piste précise du groupe correspondant — vidéo ou audio
     * selon [TrackFingerprint.trackKind] (R2 : avant, seule l'exclusion audio était implémentée,
     * une piste vidéo fautive n'était jamais désactivée) — en laissant Media3 choisir parmi les
     * pistes restantes du même groupe, ce qui distingue réellement cette étape de l'étape 3
     * (sélection d'une piste audio *précise*).
     */
    private fun applyTrackSelection(player: ExoPlayer, plan: PlaybackRepairPlan) {
        clearRepairTrackOverrides(player)
        var params = player.trackSelectionParameters.buildUpon()
        var changed = false

        plan.disabledTrack?.let { disabled ->
            val group = tracksOfKind(player, disabled.trackKind).map { it.first }
                .firstOrNull { g -> (0 until g.length).any { idx -> formatMatches(g.getTrackFormat(idx), disabled) } }
            if (group != null) {
                val remaining = (0 until group.length).filterNot { idx -> formatMatches(group.getTrackFormat(idx), disabled) }
                if (remaining.isNotEmpty()) {
                    params = params.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, remaining))
                    changed = true
                }
            }
        }

        plan.preferredAudio?.let { preferred ->
            val match = audioTracks(player).firstOrNull { (_, format) -> formatMatches(format, preferred) }
            if (match != null) {
                params = params.setOverrideForType(TrackSelectionOverride(match.first.mediaTrackGroup, match.first.indexOf(match.second)))
                changed = true
            }
        }

        if (changed) player.trackSelectionParameters = params.build()
    }

    /** Retire les overrides de piste posés par une réparation T23 précédente (R4) — audio et
     * vidéo, seuls types que [applyTrackSelection] peut avoir modifiés. */
    private fun clearRepairTrackOverrides(player: ExoPlayer) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .build()
    }

    private fun tracksOfKind(player: ExoPlayer, kind: TrackKind): List<Pair<Tracks.Group, Format>> {
        val trackType = when (kind) {
            TrackKind.AUDIO -> C.TRACK_TYPE_AUDIO
            TrackKind.VIDEO -> C.TRACK_TYPE_VIDEO
        }
        return player.currentTracks.groups
            .filter { it.type == trackType }
            .flatMap { group -> (0 until group.length).filter(group::isTrackSupported).map { group to group.getTrackFormat(it) } }
    }

    private fun audioTracks(player: ExoPlayer): List<Pair<Tracks.Group, Format>> = tracksOfKind(player, TrackKind.AUDIO)

    private fun Tracks.Group.indexOf(format: Format): Int = (0 until length).first { getTrackFormat(it) === format }

    private fun formatMatches(format: Format, fingerprint: TrackFingerprint): Boolean =
        format.sampleMimeType == fingerprint.mimeType &&
            format.language == fingerprint.language &&
            format.codecs == fingerprint.codecs &&
            format.channelCount.takeIf { it != Format.NO_VALUE } == fingerprint.channelCount &&
            format.roleFlags == fingerprint.roleFlags &&
            format.label == fingerprint.label

    private fun fingerprintOf(format: Format): TrackFingerprint = TrackFingerprint(
        trackKind = TrackKind.AUDIO,
        language = format.language,
        mimeType = format.sampleMimeType,
        codecs = format.codecs,
        channelCount = format.channelCount.takeIf { it != Format.NO_VALUE },
        roleFlags = format.roleFlags,
        label = format.label
    )
}
