package com.cstv.app.presentation.livetv.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.cstv.app.presentation.components.tvFocusHighlight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
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
import com.cstv.app.R
import com.cstv.app.domain.model.PlaybackPosition
import com.cstv.app.domain.model.FavoriteItem
import com.cstv.app.domain.model.LiveStream
import com.cstv.app.domain.model.LiveEpgProgram
import com.cstv.app.domain.model.LiveCategory
import com.cstv.app.presentation.theme.AccentLavande
import com.cstv.app.presentation.theme.DarkBackground
import com.cstv.app.presentation.theme.FavoriteGold
import com.cstv.app.presentation.theme.Surface1
import com.cstv.app.presentation.components.historyItemActions
import com.cstv.app.presentation.theme.Surface2
import com.cstv.app.presentation.theme.Surface3
import com.cstv.app.presentation.theme.BricolageGrotesque
import com.cstv.app.presentation.theme.HankenGrotesk
import com.cstv.app.presentation.theme.TextPrimary
import com.cstv.app.presentation.theme.TextSecondary
import com.cstv.app.presentation.rememberForeverLazyListState
import kotlinx.coroutines.delay

@Composable
fun CategorySectionRow(
    categoryId: String,
    title: String,
    streams: List<LiveStream>,
    favoritesList: List<FavoriteItem>,
    onToggleFavorite: (LiveStream) -> Unit,
    onStreamSelected: (LiveStream) -> Unit,
    isTv: Boolean,
    epgPrograms: Map<Int, LiveEpgProgram>,
    onLoadEpg: (Int) -> Unit,
    getScroll: (String) -> Pair<Int, Int>,
    saveScroll: (String, Int, Int) -> Unit,
    onSeeAll: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Phase 56 : titre de catégorie grisé (texte secondaire) + lien "Voir tout".
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 6.dp)
        ) {
            Text(
                text = title.uppercase(),
                color = TextSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            if (onSeeAll != null && !isTv) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Voir tout",
                    color = AccentLavande,
                    fontFamily = HankenGrotesk,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onSeeAll() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }

        val rowState = rememberForeverLazyListState("livetv_row_${categoryId}", getScroll, saveScroll)
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.fillMaxWidth().focusGroup()
        ) {
            items(streams) { stream ->
                val isFav = favoritesList.any { it.id == stream.streamId && it.type == "live" }
                if (isTv) {
                    StreamTvCard(
                        stream = stream,
                        isFavorite = isFav,
                        epgProgram = epgPrograms[stream.streamId],
                        onLoadEpg = { onLoadEpg(stream.streamId) },
                        onToggleFavorite = { onToggleFavorite(stream) },
                        onClick = { onStreamSelected(stream) }
                    )
                } else {
                    MobileStreamCard(
                        stream = stream,
                        isFavorite = isFav,
                        epgProgram = epgPrograms[stream.streamId],
                        onLoadEpg = { onLoadEpg(stream.streamId) },
                        onToggleFavorite = { onToggleFavorite(stream) },
                        onClick = { onStreamSelected(stream) }
                    )
                }
            }
        }
    }
}

@Composable
fun MobileStreamCard(
    stream: LiveStream,
    isFavorite: Boolean,
    epgProgram: LiveEpgProgram?,
    onLoadEpg: () -> Unit,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    LaunchedEffect(stream.streamId) {
        while (true) {
            onLoadEpg()
            delay(60000)
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Surface3),
        modifier = Modifier
            .width(150.dp)
            // Hauteur fixe (Phase 33) : idem, une chaîne sans EPG résolue ne
            // doit pas produire une tuile plus petite dans la rangée horizontale.
            .height(180.dp)
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Surface1),
                contentAlignment = Alignment.Center
            ) {
                if (!stream.streamIcon.isNullOrBlank()) {
                    AsyncImage(
                        model = stream.streamIcon,
                        contentDescription = stream.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(48.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = stringResource(
                                if (isFavorite) R.string.live_tv_remove_favorite
                                else R.string.live_tv_add_favorite
                            ),
                            tint = if (isFavorite) FavoriteGold else Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stream.name,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (epgProgram != null) {
                Text(
                    text = epgProgram.title,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                EpgProgressBar(
                    program = epgProgram,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                )
                Text(
                    text = epgProgram.formattedTimeRange(),
                    color = Color.Gray,
                    fontSize = 8.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Text(
                text = "CH ${stream.num}",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun CategoryFilterChip(
    category: LiveCategory,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .tvFocusHighlight(isFocused, RoundedCornerShape(16.dp))
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isFocused -> Color(0xFF2C2C35)
                    else -> Color(0xFF2A2A35)
                }
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = category.categoryName,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun EpgProgressBar(
    program: LiveEpgProgram,
    modifier: Modifier = Modifier
) {
    // Recomputed periodically so the bar keeps advancing while the program plays
    var progress by remember(program) { mutableStateOf(program.getProgressFraction()) }
    LaunchedEffect(program) {
        while (true) {
            progress = program.getProgressFraction()
            delay(30_000)
        }
    }
    LinearProgressIndicator(
        progress = { progress },
        color = MaterialTheme.colorScheme.primary,
        trackColor = Color.DarkGray,
        modifier = modifier
    )
}

@Composable
fun RecentlyWatchedRow(
    streams: List<LiveStream>,
    onStreamSelected: (LiveStream) -> Unit,
    isTv: Boolean,
    epgPrograms: Map<Int, LiveEpgProgram>,
    onLoadEpg: (Int) -> Unit,
    onLongClick: (LiveStream) -> Unit,
    getScroll: (String) -> Pair<Int, Int>,
    saveScroll: (String, Int, Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "RÉCEMMENT REGARDÉES",
            color = TextSecondary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.padding(start = 12.dp, bottom = 6.dp)
        )

        val rowState = rememberForeverLazyListState("livetv_recently_watched", getScroll, saveScroll)
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.fillMaxWidth().focusGroup()
        ) {
            items(streams) { stream ->
                if (isTv) {
                    RecentlyWatchedTvItem(
                        stream = stream,
                        epgProgram = epgPrograms[stream.streamId],
                        onLoadEpg = { onLoadEpg(stream.streamId) },
                        onClick = { onStreamSelected(stream) },
                        onLongClick = { onLongClick(stream) }
                    )
                } else {
                    MobileRecentlyWatchedItem(
                        stream = stream,
                        epgProgram = epgPrograms[stream.streamId],
                        onLoadEpg = { onLoadEpg(stream.streamId) },
                        onClick = { onStreamSelected(stream) },
                        onLongClick = { onLongClick(stream) }
                    )
                }
            }
        }
    }
}

/**
 * Phase 56 : tuile "Récemment regardées" mobile iso-maquette — logo avec numéro
 * en overlay, nom, programme en gris (et non violet), jauge de progression et
 * heures de diffusion.
 */
@Composable
fun MobileRecentlyWatchedItem(
    stream: LiveStream,
    epgProgram: LiveEpgProgram?,
    onLoadEpg: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    LaunchedEffect(stream.streamId) {
        while (true) {
            onLoadEpg()
            delay(60000)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .width(280.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Surface3)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .historyItemActions(isTv = false, onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 50.dp, height = 40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Surface1),
            contentAlignment = Alignment.Center
        ) {
            if (!stream.streamIcon.isNullOrBlank()) {
                AsyncImage(
                    model = stream.streamIcon,
                    contentDescription = stream.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(3.dp)
                )
            } else {
                Text(
                    text = "${stream.num}",
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = BricolageGrotesque
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stream.name,
                color = TextPrimary,
                fontSize = 12.5.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = HankenGrotesk
            )
            Text(
                text = epgProgram?.title ?: "Aucun programme",
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = HankenGrotesk,
                modifier = Modifier.padding(top = 1.dp)
            )
            // Jauge + heures toujours réservées (hauteur uniforme même sans EPG).
            if (epgProgram != null) {
                EpgProgressBar(
                    program = epgProgram,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 3.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(999.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 3.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                )
            }
            Text(
                text = epgProgram?.formattedTimeRange() ?: "",
                color = Color(0xFF63636F),
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontFamily = HankenGrotesk,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .heightIn(min = 13.dp)
            )
        }
    }
}

/**
 * Phase 56 : tuile de chaîne pour la grille verticale à 2 colonnes (catégorie
 * spécifique, mobile). Logo, nom, programme EPG en gris, jauge + heures ;
 * hauteur uniforme même sans EPG (Phase 33).
 */
@Composable
fun MobileChannelGridCard(
    stream: LiveStream,
    isFavorite: Boolean,
    epgProgram: LiveEpgProgram?,
    onLoadEpg: () -> Unit,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    LaunchedEffect(stream.streamId) {
        while (true) {
            onLoadEpg()
            delay(60000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Hauteur fixe (Phase 33) : une chaîne sans EPG résolue ne doit pas
            // produire une tuile plus petite que ses voisines dans la grille.
            .height(150.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Surface3)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Surface1),
            contentAlignment = Alignment.Center
        ) {
            if (!stream.streamIcon.isNullOrBlank()) {
                AsyncImage(
                    model = stream.streamIcon,
                    contentDescription = stream.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(6.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Cible tactile 48 dp, avec un fond visuel de 30 dp toujours lisible
            // même sur un logo clair.
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(48.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = stringResource(
                            if (isFavorite) R.string.live_tv_remove_favorite
                            else R.string.live_tv_add_favorite
                        ),
                        tint = if (isFavorite) FavoriteGold else Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stream.name,
            color = TextPrimary,
            fontSize = 12.5.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = HankenGrotesk
        )
        Text(
            text = epgProgram?.title ?: "Aucun programme",
            color = TextSecondary,
            fontSize = 11.sp,
            lineHeight = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = HankenGrotesk,
            modifier = Modifier.padding(top = 1.dp)
        )
        if (epgProgram != null) {
            EpgProgressBar(
                program = epgProgram,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 3.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(999.dp))
            )
            Text(
                text = epgProgram.formattedTimeRange(),
                color = Color(0xFF63636F),
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontFamily = HankenGrotesk,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun RecentlyWatchedTvItem(
    stream: LiveStream,
    epgProgram: LiveEpgProgram?,
    onLoadEpg: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(stream.streamId) {
        while (true) {
            onLoadEpg()
            delay(60000)
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) Color(0xFF23232D) else Surface3
        ),
        modifier = Modifier
            .width(180.dp)
            .height(72.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .tvFocusHighlight(isFocused, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .historyItemActions(isTv = true, onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(6.dp).fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Surface1),
                contentAlignment = Alignment.Center
            ) {
                if (!stream.streamIcon.isNullOrBlank()) {
                    AsyncImage(
                        model = stream.streamIcon,
                        contentDescription = stream.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CH ${stream.num} ${stream.name}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (epgProgram != null) {
                    Text(
                        text = epgProgram.title,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    EpgProgressBar(
                        program = epgProgram,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                    )
                    Text(
                        text = epgProgram.formattedTimeRange(),
                        color = Color.Gray,
                        fontSize = 8.sp
                    )
                }
            }
        }
    }
}

@Composable
fun StreamTvCard(
    stream: LiveStream,
    isFavorite: Boolean,
    epgProgram: LiveEpgProgram?,
    onLoadEpg: () -> Unit,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(stream.streamId) {
        while (true) {
            onLoadEpg()
            delay(60000)
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) Color(0xFF23232D) else Surface3
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .tvFocusHighlight(isFocused, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp).fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Surface1),
                contentAlignment = Alignment.Center
            ) {
                if (!stream.streamIcon.isNullOrBlank()) {
                    AsyncImage(
                        model = stream.streamIcon,
                        contentDescription = stream.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CH ${stream.num} ${stream.name}",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (epgProgram != null) {
                    Text(
                        text = epgProgram.title,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    EpgProgressBar(
                        program = epgProgram,
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                    )
                    Text(
                        text = epgProgram.formattedTimeRange(),
                        color = Color.Gray,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                } else {
                    Text(
                        text = "Aucune information de programme",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Favorite Star on focus or favorite status
            if (isFocused || isFavorite) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Favori",
                        tint = if (isFavorite) Color.Yellow else Color.DarkGray
                    )
                }
            }
        }
    }
}
