package com.cstv.app.presentation.player.core

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.video.VideoRendererEventListener
import com.cstv.app.data.download.OfflineDownloadUtil
import com.cstv.app.domain.model.PlaybackRepairPlan
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory

internal fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

/**
 * `NextRenderersFactory` dont la construction des renderers vidéo ignore le mode d'extension
 * global reçu en paramètre (réglé pour préférer FFmpeg côté audio, voir [PlayerDecoderPolicy]) et
 * force [videoExtensionRendererMode], pour que le décodeur matériel du téléviseur soit utilisé en
 * priorité pour la vidéo par défaut, sans changer la priorité audio (B16). T23 §8.1 : ce mode
 * varie désormais selon le [com.cstv.app.domain.model.DecoderStrategy] du plan de réparation en
 * cours plutôt que d'être toujours fixé à [PlayerDecoderPolicy.VIDEO_EXTENSION_RENDERER_MODE].
 */
@UnstableApi
private class VideoHardwarePreferredRenderersFactory(
    context: Context,
    private val videoExtensionRendererMode: Int
) : NextRenderersFactory(context) {
    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>
    ) {
        super.buildVideoRenderers(
            context,
            videoExtensionRendererMode,
            mediaCodecSelector,
            enableDecoderFallback,
            eventHandler,
            eventListener,
            allowedVideoJoiningTimeMs,
            out
        )
    }
}

/**
 * Construit une instance ExoPlayer selon [plan] (T23 §8.1). Adapter mince, non testable en JVM
 * sans device (AGENTS.md, §8.8) : la logique de sélection de stratégie elle-même est couverte par
 * [PlayerDecoderPolicy.videoExtensionRendererMode] (pur, testé séparément).
 */
@UnstableApi
private fun buildExoPlayer(context: Context, useOfflineCache: Boolean, plan: PlaybackRepairPlan): ExoPlayer {
    // Audio : FFmpeg (NextLib) reste préféré pour couvrir EAC3/AC3/DTS sur les appareils sans
    // décodeur matériel correspondant. Vidéo : le décodeur matériel est privilégié par défaut
    // (VideoHardwarePreferredRenderersFactory) — un décodeur logiciel préféré pour la vidéo
    // produit une image corrompue sur certains téléviseurs (cf. B16). Voir PlayerDecoderPolicy.
    val renderersFactory = VideoHardwarePreferredRenderersFactory(
        context,
        PlayerDecoderPolicy.videoExtensionRendererMode(plan)
    )
        .setExtensionRendererMode(PlayerDecoderPolicy.AUDIO_EXTENSION_RENDERER_MODE)
        .setEnableDecoderFallback(PlayerDecoderPolicy.ENABLE_DECODER_FALLBACK)

    val builder = ExoPlayer.Builder(context, renderersFactory)
    if (useOfflineCache) {
        // Réservé aux VOD/Séries : le Live conserve sa source réseau actuelle.
        builder.setMediaSourceFactory(
            DefaultMediaSourceFactory(
                OfflineDownloadUtil.getReadOnlyCacheDataSourceFactory(context)
            )
        )
    }
    return builder.build()
}

/**
 * Contrôleur partagé (T23 §8.2, tâche 4) possédant la fabrique d'ExoPlayer paramétrée par
 * [PlaybackRepairPlan], le `MediaItem` courant et le cycle stop/release/rebuild/prepare. `player`
 * est un état Compose : un [rebuild] recompose automatiquement tout lecteur de `player`.
 */
@UnstableApi
class PlaybackEngineController(private val buildPlayer: (PlaybackRepairPlan) -> ExoPlayer) {
    var player: ExoPlayer by mutableStateOf(buildPlayer(PlaybackRepairPlan.DEFAULT))
        private set
    private var currentMediaItem: androidx.media3.common.MediaItem? = null

    /**
     * Pose le `MediaItem` à lire — à appeler à la place de `player.setMediaItem` directement dès
     * qu'un appelant peut déclencher [rebuild] (câblage tâche 7) : sans passer par ici, un
     * `rebuild` ultérieur produirait un lecteur sans média attaché, `prepare()` n'ayant alors rien
     * à préparer. Les écrans qui ne reconstruisent jamais (plan par défaut seul) peuvent continuer
     * à appeler `player.setMediaItem` directement, sans régression (tâche 4).
     */
    fun setMediaItem(item: androidx.media3.common.MediaItem) {
        currentMediaItem = item
        player.setMediaItem(item)
    }

    /**
     * Reconstruit l'ExoPlayer selon [plan] : l'instance précédente est arrêtée et libérée avant
     * que la nouvelle ne soit construite (§8.7 — jamais deux instances actives en même temps). Le
     * dernier `MediaItem` posé via [setMediaItem] est réattaché à la nouvelle instance ; la
     * position et l'état de lecture restent à la charge de l'appelant (tâche 7).
     */
    fun rebuild(plan: PlaybackRepairPlan): ExoPlayer {
        releaseCurrent()
        val next = buildPlayer(plan)
        currentMediaItem?.let(next::setMediaItem)
        player = next
        return next
    }

    fun release() {
        releaseCurrent()
    }

    private fun releaseCurrent() {
        player.stop()
        player.clearVideoSurface()
        player.release()
    }
}

@UnstableApi
@Composable
fun rememberPlaybackEngineController(useOfflineCache: Boolean): PlaybackEngineController {
    val context = LocalContext.current

    val controller = remember {
        PlaybackEngineController { plan -> buildExoPlayer(context, useOfflineCache, plan) }
    }

    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    // Sur Android TV, la touche Home ramène l'Activity en arrière-plan
    // (ON_STOP) sans forcément la détruire : sans ce hook, l'ExoPlayer
    // continuait de jouer hors écran (son en tâche de fond). On coupe la
    // lecture sur ON_STOP et on la reprend sur ON_START seulement si elle
    // était active au moment du passage en arrière-plan (ne pas relancer une
    // lecture que l'utilisateur avait lui-même mise en pause). Lit
    // `controller.player` au moment de l'événement plutôt qu'une instance
    // capturée à l'ouverture : reste correct après un `rebuild`.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(controller, lifecycleOwner) {
        var wasPlayingBeforeStop = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    wasPlayingBeforeStop = controller.player.isPlaying
                    controller.player.pause()
                }
                Lifecycle.Event.ON_START -> {
                    if (wasPlayingBeforeStop) {
                        wasPlayingBeforeStop = false
                        controller.player.play()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return controller
}

@UnstableApi
@Composable
fun rememberManagedExoPlayer(useOfflineCache: Boolean): ExoPlayer = rememberPlaybackEngineController(useOfflineCache).player
