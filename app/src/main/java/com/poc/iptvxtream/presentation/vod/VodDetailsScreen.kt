package com.poc.iptvxtream.presentation.vod

import com.poc.iptvxtream.presentation.components.formatReleaseYear
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.poc.iptvxtream.presentation.theme.AccentLavande
import com.poc.iptvxtream.presentation.theme.BricolageGrotesque
import com.poc.iptvxtream.presentation.theme.HankenGrotesk
import com.poc.iptvxtream.presentation.theme.Surface1
import com.poc.iptvxtream.presentation.theme.Surface2
import com.poc.iptvxtream.presentation.theme.Surface3
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
import com.poc.iptvxtream.presentation.theme.SurfaceFocused
import com.poc.iptvxtream.presentation.theme.SurfaceElevated
import com.poc.iptvxtream.presentation.theme.WhiteOverlay20
import com.poc.iptvxtream.R
import androidx.compose.ui.res.stringResource

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
    modifier: Modifier = Modifier,
    onSearchQueryTriggered: (String) -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isTv) Surface1 else Color.Transparent)
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
                    modifier = Modifier.background(WhiteOverlay20, shape = RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.player_back), tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isTv) {
                TvLayoutDetails(
                    details = details,
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    onPlayFromBeginning = onPlayFromBeginning,
                    onResumePlayback = onResumePlayback,
                    onSearchQueryTriggered = onSearchQueryTriggered
                )
            } else {
                MobileLayoutDetails(
                    details = details,
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    onPlayFromBeginning = onPlayFromBeginning,
                    onResumePlayback = onResumePlayback,
                    onSearchQueryTriggered = onSearchQueryTriggered
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
    onResumePlayback: (Long) -> Unit,
    onSearchQueryTriggered: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        // Left Column: Poster Cover & Favorite Toggle Button
        Column(modifier = Modifier.width(220.dp)) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(310.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color.DarkGray, RoundedCornerShape(16.dp))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Surface3),
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

            Spacer(modifier = Modifier.height(12.dp))

            // TV Favorite Button in left sidebar
            var isFocusedFav by remember { mutableStateOf(false) }
            TvButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .onFocusChanged { isFocusedFav = it.isFocused }
                    .border(width = 2.dp, color = if (isFocusedFav) Color.Yellow else Color.Transparent, shape = RoundedCornerShape(8.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = if (isFavorite) Color.Yellow else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    TvText(
                        text = if (isFavorite) stringResource(R.string.details_in_favorites_upper) else stringResource(R.string.details_add_favorites_upper),
                        fontWeight = FontWeight.Bold,
                        style = TvTheme.typography.labelSmall
                    )
                }
            }
        }

        // Right Column: Full description, metadata, play options, and cast
        Column(modifier = Modifier.weight(1f)) {
            Text(details.name.uppercase(), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(8.dp))

            // Metadata row: Year, Genre, Duration, Rating
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(formatReleaseYear(details.releaseDate), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = details.plot,
                color = Color.LightGray,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 10.dp))

            // Clickable Credits: Director & Cast
            ClickableCreditsRow(label = stringResource(R.string.details_director), names = details.director, onClickName = onSearchQueryTriggered)
            Spacer(modifier = Modifier.height(6.dp))
            ClickableCreditsRow(label = stringResource(R.string.details_actors), names = details.actors, onClickName = onSearchQueryTriggered)

            Spacer(modifier = Modifier.height(24.dp))

            // Playback Options
            PlayButtonsRow(
                resumePositionMs = details.resumePositionMs,
                onPlayFromBeginning = onPlayFromBeginning,
                onResumePlayback = { onResumePlayback(details.resumePositionMs) },
                isTv = true
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
    onResumePlayback: (Long) -> Unit,
    onSearchQueryTriggered: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Centered Poster Card (just like in the movie details layout)
        Card(
            modifier = Modifier
                .width(180.dp)
                .height(270.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(Surface3),
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = details.name,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = BricolageGrotesque,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = stringResource(R.string.common_favorites),
                    tint = if (isFavorite) Color.Yellow else Color.DarkGray,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(formatReleaseYear(details.releaseDate), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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

        // Clickable Credits: Director & Cast
        ClickableCreditsRow(label = stringResource(R.string.details_director), names = details.director, onClickName = onSearchQueryTriggered)
        Spacer(modifier = Modifier.height(8.dp))
        ClickableCreditsRow(label = stringResource(R.string.details_actors), names = details.actors, onClickName = onSearchQueryTriggered)

        Spacer(modifier = Modifier.height(24.dp))

        // Playback Options
        PlayButtonsRow(
            resumePositionMs = details.resumePositionMs,
            onPlayFromBeginning = onPlayFromBeginning,
            onResumePlayback = { onResumePlayback(details.resumePositionMs) },
            isTv = false
        )
    }
}

@Composable
private fun ClickableCreditsRow(
    label: String,
    names: String,
    onClickName: (String) -> Unit
) {
    if (names.isBlank() || names == stringResource(R.string.common_unknown)) return

    val nameList = remember(names) { names.split(",").map { it.trim() }.filter { it.isNotBlank() } }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "$label : ",
                color = Color.Gray,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f).focusGroup()
            ) {
                items(nameList) { name ->
                    CreditNameChip(name = name, onClick = { onClickName(name) })
                }
            }
        }
    }
}

@Composable
private fun CreditNameChip(
    name: String,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = 1.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.DarkGray,
                shape = RoundedCornerShape(12.dp)
            )
            .background(if (isFocused) SurfaceFocused else Surface3)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = name,
            color = if (isFocused) MaterialTheme.colorScheme.primary else Color.LightGray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PlayButtonsRow(
    resumePositionMs: Long,
    onPlayFromBeginning: () -> Unit,
    onResumePlayback: () -> Unit,
    isTv: Boolean
) {
    val hasHistory = resumePositionMs > 0L

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (isTv) {
            if (hasHistory) {
                var isFocusedResume by remember { mutableStateOf(false) }
                TvButton(
                    onClick = onResumePlayback,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .onFocusChanged { isFocusedResume = it.isFocused }
                        .border(width = 2.dp, color = if (isFocusedResume) Color.Yellow else Color.Transparent, shape = RoundedCornerShape(8.dp))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        TvText(
                            text = stringResource(R.string.details_resume_upper),
                            fontWeight = FontWeight.Bold,
                            style = TvTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }
            }

            var isFocusedPlay by remember { mutableStateOf(false) }
            TvButton(
                onClick = onPlayFromBeginning,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .onFocusChanged { isFocusedPlay = it.isFocused }
                    .border(width = 2.dp, color = if (isFocusedPlay) Color.Yellow else Color.Transparent, shape = RoundedCornerShape(8.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    TvText(
                        text = if (hasHistory) "RELIRE DEPUIS LE DÉBUT" else "LIRE LE FILM",
                        fontWeight = FontWeight.Bold,
                        style = TvTheme.typography.labelMedium,
                        color = Color.White
                    )
                }
            }
        } else {
            if (hasHistory) {
                Button(
                    onClick = onResumePlayback,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.details_resume_upper), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            Button(
                onClick = onPlayFromBeginning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (hasHistory) SurfaceElevated else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (hasHistory) "RELIRE DEPUIS LE DÉBUT" else "LIRE LE FILM",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
