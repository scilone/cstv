package com.cstv.app.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cstv.app.presentation.theme.Surface3

/**
 * Composants UI partagés par les trois lecteurs (Live TV / VOD / Séries),
 * refonte player (Phase 60). Objectif : une barre de contrôle homogène —
 * boutons ronds semi-transparents en haut, transport central, actions
 * texte+icône en bas — et éviter la triplication du style.
 */

/** Couleur de fond des boutons ronds de l'overlay (semi-transparent sombre). */
private val OverlayButtonBg = Color(0x59000000)

/**
 * Jaquette actionnable commune aux players VOD et Séries. Elle garde une zone
 * interactive même sans image, pour que la navigation vers la fiche ne dépende
 * pas de la disponibilité de l'affiche.
 */
@Composable
fun PlayerCoverAction(
    coverUrl: String?,
    contentDescription: String,
    isAvailable: Boolean,
    unavailableContentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Format double sur TV (distance de lecture) ; le format compact reste de
     * mise sur mobile, où le bloc bas partage une hauteur d'écran en paysage.
     */
    large: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(
                width = if (large) 128.dp else 64.dp,
                height = if (large) 184.dp else 92.dp
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                onClickLabel = if (isAvailable) contentDescription else unavailableContentDescription,
                role = Role.Button,
                onClick = onClick
            )
            .focusable()
            .background(Surface3)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (coverUrl.isNullOrBlank()) {
            Icon(
                imageVector = Icons.Default.Movie,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(if (large) 60.dp else 30.dp)
            )
        } else {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

/**
 * Bouton rond de la barre supérieure (retour, PIP, format d'image). Bordure
 * blanche discrète qui s'illumine (couleur primaire) au focus D-pad sur TV.
 */
@Composable
fun PlayerTopButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(44.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
            .background(OverlayButtonBg, RoundedCornerShape(10.dp))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color(0x59FFFFFF),
                shape = RoundedCornerShape(10.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

/**
 * Bouton de transport central (rewind / play-pause / fast-forward). Le
 * play/pause est agrandi via [big]. Icône blanche, halo au focus.
 */
@Composable
fun TransportButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    big: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val boxSize = if (big) 76.dp else 60.dp
    val iconSize = if (big) 44.dp else 34.dp
    Box(
        modifier = modifier
            .size(boxSize)
            .onFocusChanged { isFocused = it.isFocused }
            // Le clip précède le clickable : sans lui, l'indication de clic se
            // peint sur les bornes carrées du nœud et laisse un aplat gris
            // autour du bouton rond.
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .focusable()
            .background(
                if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color(0x33000000),
                CircleShape
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}

/**
 * Action de la barre du bas : icône + libellé sur une ligne (ex.
 * « ↻ Recommencer », « ⏭ Épisode suivant », « 🔊 Audio »). Se grise si
 * [enabled] est faux. Focus D-pad matérialisé par la couleur primaire.
 */
@Composable
fun PlayerBottomAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var isFocused by remember { mutableStateOf(false) }
    val tint = when {
        !enabled -> Color(0x66FFFFFF)
        isFocused -> MaterialTheme.colorScheme.primary
        else -> Color.White
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .then(if (enabled) Modifier.focusable() else Modifier)
            .background(
                if (isFocused && enabled) Color(0x1FFFFFFF) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .alpha(if (enabled) 1f else 0.6f)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Badge résolution vidéo réelle (« 1280×720 »), pilule à bordure discrète,
 * calé à droite au niveau du titre. N'affiche rien si la taille est inconnue.
 */
@Composable
fun ResolutionBadge(
    width: Int,
    height: Int,
    modifier: Modifier = Modifier
) {
    if (width <= 0 || height <= 0) return
    Box(
        modifier = modifier
            .background(Color(0x40000000), RoundedCornerShape(6.dp))
            .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = "${width}×${height}",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Pause dessinée à la main (deux barres) : l'icône Pause du set core n'est pas
 * garantie ; ceci évite une dépendance sur material-icons-extended pour ce cas.
 */
@Composable
fun PauseGlyph(size: androidx.compose.ui.unit.Dp = 40.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(size * 0.18f)) {
        repeat(2) {
            Box(
                modifier = Modifier
                    .size(width = size * 0.28f, height = size)
                    .background(Color.White, RoundedCornerShape(2.dp))
            )
        }
    }
}

/**
 * Bouton play/pause central agrandi, avec glyphe pause dessiné à la main.
 * Partagé par les lecteurs VOD/Séries (le Live TV n'a pas de transport).
 */
@Composable
fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(76.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
            .background(
                if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color(0x33000000),
                CircleShape
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isPlaying) {
            PauseGlyph(size = 34.dp)
        } else {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Lecture",
                tint = Color.White,
                modifier = Modifier.size(44.dp)
            )
        }
    }
}
