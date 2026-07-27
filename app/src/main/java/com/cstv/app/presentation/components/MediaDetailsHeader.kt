package com.cstv.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cstv.app.domain.model.TrailerMedia
import com.cstv.app.presentation.theme.Surface1

/**
 * Part de la hauteur utile occupée par la zone de tête des fiches mobiles.
 *
 * Rapportée au conteneur — l'écran moins la barre d'état et la barre de
 * navigation — et non à l'écran entier : à 0,45, le bas de l'image tombe au
 * même niveau que sur les applications de référence, dont le visuel occupe
 * environ 45 % de la dalle en débordant sous la barre d'état.
 */
const val MEDIA_DETAILS_HEADER_HEIGHT_FRACTION = 0.45f

/**
 * Zone de tête des fiches de détail **mobile** : l'image du média occupe toute
 * la largeur sur environ un tiers de la hauteur, et le trailer vient s'y
 * substituer une fois prêt.
 *
 * Le bloc est opaque par construction : rempli par l'image puis par la vidéo,
 * il masque naturellement le contenu qui défile dessous quand il est épinglé.
 *
 * Le bouton Son n'apparaît qu'une fois la vidéo réellement révélée — pendant la
 * phase de chargement, il n'y a encore rien à écouter. Même règle que le
 * carrousel de l'Accueil.
 */
@Composable
fun MediaDetailsHeader(
    imageUrl: String?,
    contentDescription: String?,
    media: TrailerMedia,
    trailerState: TrailerPreviewUiState,
    muted: Boolean,
    onMutedChange: (Boolean) -> Unit,
    onTrailerFailed: (TrailerMedia) -> Unit,
    onBack: () -> Unit,
    height: Dp,
    modifier: Modifier = Modifier
) {
    var trailerRevealed by remember(media) { mutableStateOf(false) }
    val trailerPlaying = (trailerState as? TrailerPreviewUiState.Playing)?.preview?.media == media

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(Surface1)
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (trailerPlaying) {
            MediaDetailsTrailerBackdrop(
                media = media,
                state = trailerState,
                // L'image du bloc sert de couverture pendant le chargement :
                // le lecteur n'a pas besoin de la sienne.
                posterUrl = null,
                onPlaybackFailed = onTrailerFailed,
                muted = muted,
                onRevealed = { trailerRevealed = true },
                fadeInOnReveal = true,
                modifier = Modifier.matchParentSize()
            )
        }

        // Assombrit le haut de la zone pour garder les commandes lisibles quel
        // que soit le visuel qui passe dessous.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)
                    )
                )
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

        if (trailerPlaying && trailerRevealed) {
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
