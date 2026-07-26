package com.cstv.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cstv.app.domain.model.TrailerMedia
import com.cstv.app.domain.model.TrailerSource

/**
 * Déclenche la résolution du trailer **dès que la fiche est visible**, et
 * l'arrête à la sortie.
 *
 * Volontairement sans temporisation : c'est le modèle de l'Accueil
 * (`HomeTrendingCarousel` appelle `onActiveItemChanged` immédiatement). La
 * résolution coûte jusqu'à trois appels réseau ; l'attendre pour *commencer*
 * ajoutait ce délai à celui du chargement de la WebView. L'attente perçue par
 * l'utilisateur est portée par le poster de couverture interne au lecteur
 * (`REVEAL_DELAY_MS`), qui masque la phase de chargement puis s'efface en fondu.
 *
 * Effet pur (aucun rendu) : le déclenchement doit rester monté même quand
 * aucun trailer n'est disponible, alors que l'affichage du lecteur, lui, est
 * conditionnel.
 */
@Composable
fun TrailerAutoStartEffect(
    media: TrailerMedia,
    onContextReady: (TrailerMedia) -> Unit,
    onContextEnded: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var lifecycleStarted by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            lifecycleStarted = event == Lifecycle.Event.ON_START ||
                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (event == Lifecycle.Event.ON_STOP) onContextEnded()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(media, lifecycleStarted) {
        if (lifecycleStarted) onContextReady(media)
    }
    DisposableEffect(media) { onDispose(onContextEnded) }
}

/**
 * Décor non interactif : la fiche garde toujours la priorité tactile et D-Pad.
 *
 * Ne porte aucune logique de déclenchement (voir [TrailerAutoStartEffect]) : ce
 * composable peut donc être monté seulement quand un trailer est effectivement
 * disponible, ce qui permet à la fiche mobile de basculer de l'affiche vers un
 * bandeau 16:9 sans jamais empêcher la résolution de démarrer.
 *
 * [scrimAlpha] assombrit le trailer pour préserver la lisibilité du texte posé
 * par-dessus (fond plein écran TV). En bandeau, aucun texte ne le recouvre :
 * l'appelant passe `0f` pour laisser l'image intacte.
 */
@Composable
fun MediaDetailsTrailerBackdrop(
    media: TrailerMedia,
    state: TrailerPreviewUiState,
    posterUrl: String?,
    onPlaybackFailed: (TrailerMedia) -> Unit,
    muted: Boolean,
    modifier: Modifier = Modifier,
    onRevealed: () -> Unit = {},
    scrimAlpha: Float = 0f
) {
    Box(modifier) {
        val preview = (state as? TrailerPreviewUiState.Playing)?.preview
        val source = preview?.source as? TrailerSource.YouTube
        if (preview?.media == media && source != null) {
            YouTubeTrailerPreview(
                videoId = source.videoId,
                muted = muted,
                posterUrl = posterUrl,
                onPlaybackError = { onPlaybackFailed(media) },
                onRevealed = onRevealed,
                modifier = Modifier.fillMaxSize().focusProperties { canFocus = false }
            )
            if (scrimAlpha > 0f) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrimAlpha)))
            }
        }
    }
}
