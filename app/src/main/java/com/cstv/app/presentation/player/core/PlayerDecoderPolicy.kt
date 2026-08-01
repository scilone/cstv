package com.cstv.app.presentation.player.core

import androidx.media3.exoplayer.DefaultRenderersFactory

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
    const val ENABLE_DECODER_FALLBACK = true
}
