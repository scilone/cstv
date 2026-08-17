package com.cstv.app.presentation.player.core

import androidx.media3.exoplayer.DefaultRenderersFactory
import com.cstv.app.domain.model.DecoderStrategy
import com.cstv.app.domain.model.PlaybackRepairPlan
import com.cstv.app.domain.model.TrackKind

/**
 * Politique de décodage asymétrique appliquée à `NextRenderersFactory` (voir B16).
 *
 * - Audio : les renderers d'extension FFmpeg (NextLib) restent *préférés*
 *   ([AUDIO_EXTENSION_RENDERER_MODE]) pour couvrir EAC3/AC3/DTS sur les appareils sans décodeur
 *   matériel correspondant — comportement d'origine inchangé.
 * - Vidéo : les renderers d'extension FFmpeg ne sont retenus qu'en dernier recours
 *   ([VIDEO_EXTENSION_RENDERER_MODE]), pour que le décodeur matériel du téléviseur soit utilisé
 *   en priorité. Le préférer produisait une image corrompue sur certains téléviseurs.
 *
 * `DefaultRenderersFactory.setExtensionRendererMode` étant un réglage global partagé entre audio
 * et vidéo, ces deux modes ne peuvent pas coexister via ce seul champ : la construction des
 * renderers vidéo doit forcer [VIDEO_EXTENSION_RENDERER_MODE] indépendamment du mode global
 * configuré pour l'audio (voir la sous-classe dédiée dans `ExoPlayerCore.kt`).
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object PlayerDecoderPolicy {

    const val AUDIO_EXTENSION_RENDERER_MODE = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
    const val VIDEO_EXTENSION_RENDERER_MODE = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
    const val VIDEO_EXTENSION_RENDERER_MODE_SOFTWARE_PREFERRED = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
    const val ENABLE_DECODER_FALLBACK = true

    /**
     * T23 §8.1 (review R2) : [DecoderStrategy.SOFTWARE_PREFERRED] ne bascule la vidéo en logiciel
     * préféré que si c'est bien la vidéo qui a échoué ([PlaybackRepairPlan.softwarePreferredTrackKind]
     * == [TrackKind.VIDEO]). Une erreur audio identifiée (`trackKind == AUDIO`) laisse la vidéo au
     * matériel : l'audio préfère déjà FFmpeg par défaut, ce plan n'y change rien. `null` (piste
     * fautive non identifiée, séquence à l'aveugle §7.4) reste conservateur et tente aussi le
     * logiciel côté vidéo, comme avant ce correctif — l'ancien comportement forçait toujours cette
     * bascule, ce qui est correct seulement dans ce cas d'incertitude.
     */
    fun videoExtensionRendererMode(plan: PlaybackRepairPlan): Int = when {
        plan.decoderStrategy == DecoderStrategy.DEFAULT -> VIDEO_EXTENSION_RENDERER_MODE
        plan.softwarePreferredTrackKind == TrackKind.AUDIO -> VIDEO_EXTENSION_RENDERER_MODE
        else -> VIDEO_EXTENSION_RENDERER_MODE_SOFTWARE_PREFERRED
    }
}
