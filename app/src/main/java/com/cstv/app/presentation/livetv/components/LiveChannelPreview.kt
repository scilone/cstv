package com.cstv.app.presentation.livetv.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.cstv.app.domain.model.Credentials
import com.cstv.app.domain.model.LiveStream
import com.cstv.app.presentation.player.core.rememberManagedExoPlayer
import kotlinx.coroutines.delay

/** F25 — délai de focus stable avant que la miniature démarre son aperçu vidéo. */
internal const val PREVIEW_FOCUS_DELAY_MS = 1_000L

/**
 * Résout la chaîne candidate à l'aperçu à partir d'un événement de focus.
 * Fonction pure (F25), testable en JVM sans Compose ni ExoPlayer : un
 * événement `focused = false` n'efface le candidat que s'il porte encore sur
 * la chaîne actuellement candidate — un relâchement de focus périmé (arrivant
 * après qu'une carte plus récente a déjà pris le focus) ne doit pas effacer ce
 * candidat plus récent.
 */
internal fun resolvePreviewCandidate(currentCandidateId: Int?, streamId: Int, focused: Boolean): Int? =
    when {
        focused -> streamId
        currentCandidateId == streamId -> null
        else -> currentCandidateId
    }

/**
 * État de l'aperçu vidéo des chaînes Live TV (F25). Une seule instance par
 * écran, hissée dans `TvLayout` : les cartes ne font que déclarer leur focus
 * via [onFocusChanged], jamais d'instancier de lecteur.
 */
@Stable
class LiveChannelPreviewState internal constructor(
    val player: androidx.media3.exoplayer.ExoPlayer
) {
    // F25-R1 : mise à jour en place à chaque recomposition (voir SideEffect
    // dans rememberLiveChannelPreviewState), jamais reconstruite — sinon un
    // changement de credentials/disponibilité réseau crée une nouvelle
    // instance sans jamais arrêter le lecteur de l'ancienne.
    internal var credentials: Credentials? = null

    var candidateId by mutableStateOf<Int?>(null)
        private set
    private var candidateStream: LiveStream? = null

    var activeStreamId by mutableStateOf<Int?>(null)
        private set

    private val failedStreamIds = mutableSetOf<Int>()

    /** Une carte de chaîne déclare son focus ; jamais plusieurs cartes à la fois. */
    fun onFocusChanged(stream: LiveStream, focused: Boolean) {
        if (focused) candidateStream = stream
        candidateId = resolvePreviewCandidate(candidateId, stream.streamId, focused)
    }

    /** Coupe l'aperçu immédiatement — avant navigation vers le lecteur principal. */
    fun stop() {
        candidateId = null
        activeStreamId = null
        player.stop()
        player.clearMediaItems()
    }

    // F25-R3 : `mediaItemFactory` par défaut appelle `MediaItem.fromUri`, qui
    // passe par `android.net.Uri.parse` — non mockable en JVM sans Robolectric
    // (absent du projet, voir AndroidNewEpisodeNotifierTest). Le paramètre
    // permet aux tests d'injecter un `MediaItem` sans toucher cette API Android.
    internal fun activate(streamId: Int, mediaItemFactory: (String) -> MediaItem = MediaItem::fromUri) {
        val creds = credentials ?: return
        val stream = candidateStream?.takeIf { it.streamId == streamId } ?: return
        if (streamId in failedStreamIds) return
        val url = stream.getPlayUrl(baseUrl = creds.baseUrl, username = creds.username, password = creds.password)
        activeStreamId = streamId
        player.setMediaItem(mediaItemFactory(url))
        player.prepare()
        player.play()
    }

    internal fun deactivate() {
        if (activeStreamId == null) return
        activeStreamId = null
        player.stop()
        player.clearMediaItems()
    }

    internal fun markFailed(streamId: Int) {
        failedStreamIds += streamId
        if (activeStreamId == streamId) deactivate()
    }
}

/**
 * F25-R3 : cœur temporel de l'aperçu, extrait de `LaunchedEffect` pour être
 * testable en JVM (`kotlinx-coroutines-test`) sans Compose ni ExoPlayer.
 * Un changement de candidat annule cet appel (la coroutine `LaunchedEffect`
 * est relancée par Compose), donc [activate] n'est jamais invoqué pour un
 * candidat déjà remplacé.
 */
internal suspend fun activatePreviewAfterDelay(
    enabled: Boolean,
    candidateId: Int?,
    delayMs: Long = PREVIEW_FOCUS_DELAY_MS,
    deactivate: () -> Unit,
    activate: (Int) -> Unit
) {
    if (!enabled || candidateId == null) {
        deactivate()
        return
    }
    delay(delayMs)
    activate(candidateId)
}

/**
 * Un flux Live exige toujours le serveur : hors ligne ou sans identifiants,
 * [enabled] doit être faux et aucun aperçu n'est jamais tenté (F25).
 */
@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun rememberLiveChannelPreviewState(
    enabled: Boolean,
    credentials: Credentials?
): LiveChannelPreviewState {
    val player = rememberManagedExoPlayer(useOfflineCache = false)
    // F25-R1 : une seule instance par lecteur, jamais recréée quand `enabled`
    // ou `credentials` changent (ex: perte réseau détectée en cours d'aperçu)
    // — sinon l'ancienne instance est abandonnée sans que son lecteur soit
    // arrêté. `credentials` est répercuté en place via le SideEffect ci-dessous.
    val state = remember(player) { LiveChannelPreviewState(player) }
    SideEffect { state.credentials = credentials }

    DisposableEffect(player, state) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                state.activeStreamId?.let { state.markFailed(it) }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, state) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) state.stop()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state, state.candidateId, enabled) {
        activatePreviewAfterDelay(
            enabled = enabled,
            candidateId = state.candidateId,
            deactivate = state::deactivate,
            activate = state::activate
        )
    }

    return state
}
