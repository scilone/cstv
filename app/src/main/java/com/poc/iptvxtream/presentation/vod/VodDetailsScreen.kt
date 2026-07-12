package com.poc.iptvxtream.presentation.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme as TvTheme
import androidx.tv.material3.Text as TvText
import coil.compose.AsyncImage
import com.poc.iptvxtream.domain.model.VodDetails

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun VodDetailsScreen(
    details: VodDetails,
    isTv: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onPlayFromBeginning: () -> Unit,
    onResumePlayback: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F13))
    ) {
        // 1. Cinematic Blurred Backdrop Cover Image
        if (!details.coverBig.isNullOrBlank()) {
            AsyncImage(
                model = details.coverBig,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(20.dp)
                    .alpha(0.18f)
            )
        }

        // 2. Content Column/Scroll
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            // Back Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.background(Color(0x33FFFFFF), shape = RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text("Détails du Film", color = Color.Gray, fontSize = 14.sp, fontWeight = FontWeight.Light)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Main Details Row
            if (isTv) {
                TvLayoutDetails(
                    details = details,
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    onPlayFromBeginning = onPlayFromBeginning,
                    onResumePlayback = onResumePlayback
                )
            } else {
                MobileLayoutDetails(
                    details = details,
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    onPlayFromBeginning = onPlayFromBeginning,
                    onResumePlayback = onResumePlayback
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvLayoutDetails(
    details: VodDetails,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onPlayFromBeginning: () -> Unit,
    onResumePlayback: (Long) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        // Left Column: Large Poster Card
        Card(
            modifier = Modifier
                .width(240.dp)
                .height(360.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, Color.DarkGray, RoundedCornerShape(16.dp))
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E24)),
                contentAlignment = Alignment.Center
            ) {
                if (!details.coverBig.isNullOrBlank()) {
                    AsyncImage(
                        model = details.coverBig,
                        contentDescription = details.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                }
            }
        }

        // Right Column: Text & Play Buttons
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = details.name.uppercase(),
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Metadata row: Year, Genre, Duration, Rating
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(details.releaseDate, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("|", color = Color.DarkGray)
                Text(
                    text = details.genre,
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                details.duration?.let { dur ->
                    Text("|", color = Color.DarkGray)
                    Text(dur, color = Color.LightGray, fontSize = 13.sp)
                }
                Text("|", color = Color.DarkGray)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(details.rating, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Plot
            Text(
                text = details.plot,
                color = Color.LightGray,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))

            // Credits
            Text("Réalisateur : ${details.director}", color = Color.Gray, fontSize = 13.sp)
            Text("Acteurs : ${details.actors}", color = Color.Gray, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)

            Spacer(modifier = Modifier.height(28.dp))

            // Playback Options
            PlayButtonsRow(
                details = details,
                isTv = true,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                onPlayFromBeginning = onPlayFromBeginning,
                onResumePlayback = onResumePlayback
            )
        }
    }
}

@Composable
private fun MobileLayoutDetails(
    details: VodDetails,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onPlayFromBeginning: () -> Unit,
    onResumePlayback: (Long) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Centered Poster Card
        Card(
            modifier = Modifier
                .width(180.dp)
                .height(270.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E24)),
                contentAlignment = Alignment.Center
            ) {
                if (!details.coverBig.isNullOrBlank()) {
                    AsyncImage(
                        model = details.coverBig,
                        contentDescription = details.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(54.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Text details
        Text(
            text = details.name,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(details.releaseDate, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("  •  ", color = Color.DarkGray)
            Text(
                text = details.genre,
                color = Color.LightGray,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            details.duration?.let { dur ->
                Text("  •  ", color = Color.DarkGray)
                Text(dur, color = Color.LightGray, fontSize = 12.sp)
            }
            Text("  •  ", color = Color.DarkGray)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text(details.rating, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = details.plot,
            color = Color.LightGray,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Justify,
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))

        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            Text("Réalisateur : ${details.director}", color = Color.Gray, fontSize = 12.sp)
            Text("Acteurs : ${details.actors}", color = Color.Gray, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Playback Options
        PlayButtonsRow(
            details = details,
            isTv = false,
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            onPlayFromBeginning = onPlayFromBeginning,
            onResumePlayback = onResumePlayback
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayButtonsRow(
    details: VodDetails,
    isTv: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onPlayFromBeginning: () -> Unit,
    onResumePlayback: (Long) -> Unit
) {
    val hasSavedProgress = details.resumePositionMs > 0L && details.resumePositionMs < (details.durationMs - 15000L)
    
    val formattedTime = remember(details.resumePositionMs) {
        formatPlaybackTime(details.resumePositionMs)
    }

    if (isTv) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (hasSavedProgress) {
                var isFocusedResume by remember { mutableStateOf(false) }
                TvButton(
                    onClick = { onResumePlayback(details.resumePositionMs) },
                    modifier = Modifier
                        .height(44.dp)
                        .onFocusChanged { isFocusedResume = it.isFocused }
                        .border(width = 2.dp, color = if (isFocusedResume) Color.Yellow else Color.Transparent, shape = RoundedCornerShape(8.dp))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = if (isFocusedResume) Color.Black else Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        TvText("REPRENDRE À $formattedTime", fontWeight = FontWeight.Bold, style = TvTheme.typography.labelMedium)
                    }
                }

                var isFocusedRestart by remember { mutableStateOf(false) }
                TvButton(
                    onClick = onPlayFromBeginning,
                    modifier = Modifier
                        .height(44.dp)
                        .onFocusChanged { isFocusedRestart = it.isFocused }
                        .border(width = 2.dp, color = if (isFocusedRestart) Color.Yellow else Color.Transparent, shape = RoundedCornerShape(8.dp))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = if (isFocusedRestart) Color.Black else Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        TvText("RECOMMENCER", fontWeight = FontWeight.Bold, style = TvTheme.typography.labelMedium)
                    }
                }
            } else {
                var isFocusedPlay by remember { mutableStateOf(false) }
                TvButton(
                    onClick = onPlayFromBeginning,
                    modifier = Modifier
                        .height(44.dp)
                        .onFocusChanged { isFocusedPlay = it.isFocused }
                        .border(width = 2.dp, color = if (isFocusedPlay) Color.Yellow else Color.Transparent, shape = RoundedCornerShape(8.dp))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = if (isFocusedPlay) Color.Black else Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        TvText("LIRE LE FILM", fontWeight = FontWeight.Bold, style = TvTheme.typography.labelMedium)
                    }
                }
            }

            // TV Favorite Button
            var isFocusedFav by remember { mutableStateOf(false) }
            TvButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .height(44.dp)
                    .onFocusChanged { isFocusedFav = it.isFocused }
                    .border(width = 2.dp, color = if (isFocusedFav) Color.Yellow else Color.Transparent, shape = RoundedCornerShape(8.dp))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = if (isFavorite) Color.Yellow else if (isFocusedFav) Color.Black else Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    TvText(
                        text = if (isFavorite) "RETIRER DES FAVORIS" else "AJOUTER AUX FAVORIS",
                        fontWeight = FontWeight.Bold,
                        style = TvTheme.typography.labelMedium
                    )
                }
            }
        }
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (hasSavedProgress) {
                Button(
                    onClick = { onResumePlayback(details.resumePositionMs) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("REPRENDRE À $formattedTime", fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = onPlayFromBeginning,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("RECOMMENCER", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Button(
                    onClick = onPlayFromBeginning,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("LIRE LE FILM", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Mobile Favorite Toggle Button
            OutlinedButton(
                onClick = onToggleFavorite,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = if (isFavorite) Color.Yellow else Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isFavorite) "RETIRER DES FAVORIS" else "AJOUTER AUX FAVORIS",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun formatPlaybackTime(ms: Long): String {
    val totalSec = ms / 1000
    val hr = totalSec / 3600
    val min = (totalSec % 3600) / 60
    val sec = totalSec % 60
    return if (hr > 0) {
        "${hr}h ${min}m"
    } else {
        "${min}:${String.format("%02d", sec)}"
    }
}
