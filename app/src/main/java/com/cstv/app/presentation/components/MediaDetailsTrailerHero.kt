package com.cstv.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cstv.app.domain.model.TrailerMedia

/**
 * Bandeau de tête des fiches de détail **mobile** : le trailer occupe toute la
 * largeur au format natif 16:9, au-dessus du titre du média.
 *
 * Remplace l'affiche centrée tant qu'un trailer est disponible — l'affiche
 * recadrée en bandeau reste visible pendant la phase de couverture du lecteur,
 * puis s'efface en fondu sur la vidéo.
 *
 * Le bouton Son n'apparaît qu'une fois la vidéo réellement révélée : pendant la
 * couverture, il n'y a encore rien à écouter. Même règle que le carrousel de
 * l'Accueil.
 */
@Composable
fun MediaDetailsTrailerHero(
    media: TrailerMedia,
    state: TrailerPreviewUiState,
    posterUrl: String?,
    muted: Boolean,
    onMutedChange: (Boolean) -> Unit,
    onPlaybackFailed: (TrailerMedia) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val videoId = ((state as? TrailerPreviewUiState.Playing)
        ?.preview
        ?.takeIf { it.media == media }
        ?.source as? com.cstv.app.domain.model.TrailerSource.YouTube)
        ?.videoId
    var revealed by remember(videoId) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black)
    ) {
        MediaDetailsTrailerBackdrop(
            media = media,
            state = state,
            posterUrl = posterUrl,
            onPlaybackFailed = onPlaybackFailed,
            muted = muted,
            onRevealed = { revealed = true },
            scrimAlpha = 0f,
            modifier = Modifier.fillMaxSize()
        )

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(Color(0x33FFFFFF), shape = RoundedCornerShape(12.dp))
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Color.White)
        }

        if (revealed) {
            IconButton(
                onClick = { onMutedChange(!muted) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = .55f), CircleShape)
            ) {
                Icon(
                    imageVector = if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                    contentDescription = if (muted) "Activer le son du trailer" else "Couper le son du trailer",
                    tint = Color.White
                )
            }
        }
    }
}
