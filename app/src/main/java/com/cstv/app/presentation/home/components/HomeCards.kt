package com.cstv.app.presentation.home.components
import com.cstv.app.R
import androidx.compose.ui.res.stringResource
import com.cstv.app.domain.model.EpisodeLabel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text as TvText
import coil.compose.AsyncImage
import com.cstv.app.domain.model.PlaybackPosition
import com.cstv.app.domain.model.FavoriteItem
import com.cstv.app.domain.model.LiveStream
import com.cstv.app.domain.model.VodStream
import com.cstv.app.domain.model.SeriesStream
import com.cstv.app.domain.model.DownloadedItem
import com.cstv.app.presentation.theme.AccentLavande
import com.cstv.app.presentation.theme.DarkBackground
import com.cstv.app.presentation.theme.Surface1
import com.cstv.app.presentation.components.historyItemActions
import com.cstv.app.presentation.theme.Surface2
import com.cstv.app.presentation.theme.Surface3
import com.cstv.app.presentation.theme.BricolageGrotesque
import com.cstv.app.presentation.theme.HankenGrotesk

@Composable
fun HomeHeroCard(
    position: PlaybackPosition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val progress = if (position.durationMs > 0) {
        position.positionMs.toFloat() / position.durationMs.toFloat()
    } else 0f

    val subtitle = if (position.type == "series") {
        val episodeLabel = EpisodeLabel.format(position.seasonNum ?: 1, position.episodeNum ?: 1)
        episodeLabel + if (!position.duration.isNullOrBlank()) " · ${position.duration}" else ""
    } else {
        if (!position.duration.isNullOrBlank()) position.duration else "Film"
    }

    val typeBadgeText = if (position.type == "series") "4K · SÉRIE" else "4K · FILM"

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = modifier
            .fillMaxWidth()
            .height(282.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = 2.dp,
                color = if (isFocused) AccentLavande else Color.Transparent,
                shape = RoundedCornerShape(22.dp)
            )
            .clip(RoundedCornerShape(22.dp))
            .clickable { onClick() }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background Image
            if (!position.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = position.coverUrl,
                    contentDescription = position.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Surface1)
                )
            }

            // Vertical Fade Overlay (Mockup-like)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.03f to DarkBackground,
                                0.52f to DarkBackground.copy(alpha = 0.10f),
                                1.00f to DarkBackground.copy(alpha = 0.42f)
                            )
                        )
                    )
            )

            // Top-left Badges
            Row(
                modifier = Modifier
                    .padding(14.dp)
                    .align(Alignment.TopStart),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                // REPRENDRE Badge
                Box(
                    modifier = Modifier
                        .background(AccentLavande, shape = RoundedCornerShape(7.dp))
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "REPRENDRE",
                        color = Color(0xFF0E0E12),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 0.05.sp,
                        fontFamily = HankenGrotesk
                    )
                }

                // Type Badge (Glass style)
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(7.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(7.dp))
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = typeBadgeText,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 0.05.sp,
                        fontFamily = HankenGrotesk
                    )
                }
            }

            // Bottom Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Text(
                    text = position.title ?: "Sans titre",
                    fontFamily = BricolageGrotesque,
                    fontWeight = FontWeight.Bold,
                    fontSize = 27.sp,
                    lineHeight = 30.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = subtitle,
                    fontFamily = HankenGrotesk,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.5.sp,
                    color = Color(0xFFC9C9D4),
                    modifier = Modifier.padding(top = 5.dp)
                )

                // Buttons Row
                Row(
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Play Button
                    Row(
                        modifier = Modifier
                            .background(Color.White, shape = RoundedCornerShape(12.dp))
                            .padding(horizontal = 22.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color(0xFF0E0E12),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Reprendre",
                            color = Color(0xFF0E0E12),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            fontFamily = HankenGrotesk
                        )
                    }

                    // Plus / Info Button (Glass style)
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.White.copy(alpha = 0.14f), shape = RoundedCornerShape(12.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Progress Bar
                LinearProgressIndicator(
                    progress = { progress },
                    color = AccentLavande,
                    trackColor = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(999.dp))
                )
            }
        }
    }
}

@Composable
fun HomeResumeWatchingCard(
    position: PlaybackPosition,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    isTv: Boolean = false,
    modifier: Modifier = Modifier.width(220.dp)
) {
    var isFocused by remember { mutableStateOf(false) }
    val progress = if (position.durationMs > 0) {
        position.positionMs.toFloat() / position.durationMs.toFloat()
    } else 0f

    // Phase 55 : ligne meta = "S01 E03 · {temps restant}" en accent, sous le titre.
    val metaText = buildString {
        if (position.type == "series") {
            EpisodeLabel.format(position.seasonNum, position.episodeNum)?.let { append(it) }
        }
        if (position.durationMs > 0) {
            val remainingMs = (position.durationMs - position.positionMs).coerceAtLeast(0L)
            if (isNotEmpty()) append(" · ")
            append(formatRemainingTime(remainingMs))
        }
    }

    Column(
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = 2.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .historyItemActions(isTv, onClick, onLongClick)
            .background(Surface3)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(124.dp)
                .background(Surface1)
        ) {
            if (!position.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = position.coverUrl,
                    contentDescription = position.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(36.dp))
                }
            }

            // Dark semi-transparent gradient at bottom
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xCC000000)),
                            startY = 60f
                        )
                    )
            )

            // Play Icon Overlay in center
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(36.dp)
                    .align(Alignment.Center)
                    .background(Color(0x80000000), shape = RoundedCornerShape(18.dp))
                    .padding(6.dp)
            )

            // Progress bar
            LinearProgressIndicator(
                progress = { progress },
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.DarkGray,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .align(Alignment.BottomCenter)
            )
        }

        // Title + temps restant (Phase 55)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Text(
                text = position.title ?: stringResource(R.string.home_no_title),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (metaText.isNotBlank()) {
                Text(
                    text = metaText,
                    color = AccentLavande,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = HankenGrotesk,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

/** Phase 55 : formate un temps restant en ms sous forme "23 min" / "1 h 05" + "restant". */
private fun formatRemainingTime(remainingMs: Long): String {
    val totalMinutes = (remainingMs / 60_000L).toInt()
    if (totalMinutes <= 0) return "Bientôt terminé"
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        "%dh%02d restant".format(hours, minutes)
    } else {
        "$minutes min restant"
    }
}

@Composable
fun HomeFavoriteItemCard(
    favorite: FavoriteItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.width(130.dp)
) {
    var isFocused by remember { mutableStateOf(false) }

    // Phase 55 : titre en overlay dans la vignette (maquette), plus en dessous.
    Box(
        modifier = modifier
            .height(195.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = 2.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .background(Surface1),
        contentAlignment = Alignment.Center
    ) {
        if (!favorite.cover.isNullOrBlank()) {
            AsyncImage(
                model = favorite.cover,
                contentDescription = favorite.name,
                // Les logos de chaînes Live TV (Phase 34) sont au format carré :
                // les faire tenir entièrement (Fit) plutôt que les rogner (Crop),
                // qui reste adapté aux affiches films/séries déjà proches du 2:3.
                contentScale = if (favorite.type == "live") ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color.DarkGray,
                modifier = Modifier.size(32.dp)
            )
        }

        // Type Badge (top left) : style neutre "sans couleur", cohérent avec
        // le badge du carrousel Tendances (HomeTrendingCarousel).
        val badgeLabel = when (favorite.type) {
            "live" -> stringResource(R.string.home_live_badge)
            "movie" -> stringResource(R.string.home_movie_badge)
            "series" -> stringResource(R.string.home_series_badge)
            else -> "FAV"
        }
        HomeMediaTypeBadge(
            label = badgeLabel,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
        )
    }
}

@Composable
fun HomeLiveTvCard(
    stream: LiveStream,
    epgProgram: com.cstv.app.domain.model.LiveEpgProgram?,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    // Phase 42 : le polling EPG (toutes les 60s) est centralisé dans
    // HomeViewModel (un seul ticker pour toute la rangée) au lieu d'une
    // boucle par carte visible ; cette carte ne fait plus que lire
    // state.epgPrograms.
    // Phase 55 : tuile horizontale conforme à la maquette (logo + numéro,
    // nom + programme, jauge de progression + heures), hauteur uniforme.
    Column(
        modifier = Modifier
            .width(180.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.07f),
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .background(Surface3)
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Logo de la chaîne
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Surface1),
                contentAlignment = Alignment.Center
            ) {
                if (!stream.streamIcon.isNullOrBlank()) {
                    AsyncImage(
                        model = stream.streamIcon,
                        contentDescription = stream.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(3.dp)
                    )
                } else {
                    Text(
                        text = stream.name.firstOrNull()?.uppercase() ?: "?",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = BricolageGrotesque
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stream.name,
                    color = Color(0xFFF2F2F6),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = HankenGrotesk
                )
                Text(
                    text = epgProgram?.title ?: stringResource(R.string.home_no_program),
                    color = Color(0xFF9A9AA8),
                    fontSize = 11.sp,
                    lineHeight = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = HankenGrotesk,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }

        // Jauge de progression (barre vide si pas d'EPG -> hauteur uniforme, Phase 33)
        if (epgProgram != null) {
            HomeEpgProgressBar(
                program = epgProgram,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(999.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.1f))
            )
        }

        // Heures de début/fin (espace réservé si pas d'EPG, hauteur uniforme)
        Text(
            text = epgProgram?.formattedTimeRange() ?: "",
            color = Color(0xFF63636F),
            fontSize = 10.sp,
            lineHeight = 12.sp,
            maxLines = 1,
            fontFamily = HankenGrotesk,
            modifier = Modifier
                .padding(top = 2.dp)
                .heightIn(min = 13.dp)
        )
    }
}

@Composable
fun HomeEpgProgressBar(
    program: com.cstv.app.domain.model.LiveEpgProgram,
    modifier: Modifier = Modifier
) {
    // Recomputed periodically so the bar keeps advancing while the program plays
    var progress by remember(program) { mutableStateOf(program.getProgressFraction()) }
    LaunchedEffect(program) {
        while (true) {
            progress = program.getProgressFraction()
            kotlinx.coroutines.delay(30_000)
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
fun HomeVodMovieCard(
    stream: VodStream,
    onClick: () -> Unit,
    rank: Int? = null,
    onLongClick: (() -> Unit)? = null,
    isTv: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }

    val rankWidth = if (rank == 10) 112.dp else 74.dp
    val cardWidth = if (rank == null) 130.dp else 130.dp + rankWidth - 30.dp
    val posterModifier = Modifier
        .width(130.dp)
        .fillMaxHeight()
        .border(
            width = 2.dp,
            color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
            shape = RoundedCornerShape(14.dp)
        )
        .clip(RoundedCornerShape(14.dp))
        .background(Surface1)

    Box(
        modifier = Modifier
            .width(cardWidth)
            .height(195.dp) // standard 2:3 ratio
            .onFocusChanged { isFocused = it.isFocused }
            .historyItemActions(isTv, onClick, onLongClick),
        contentAlignment = Alignment.Center
    ) {
        rank?.let { TopRankBadge(it, Modifier.align(Alignment.BottomStart)) }

        Box(modifier = posterModifier.align(Alignment.CenterEnd)) {
            if (!stream.streamIcon.isNullOrBlank()) {
                AsyncImage(
                    model = stream.streamIcon,
                    contentDescription = stream.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color.DarkGray,
                    modifier = Modifier.size(32.dp).align(Alignment.Center)
                )
            }

            val cleanRating = stream.rating?.trim()
            if (!cleanRating.isNullOrBlank() && cleanRating != "0" && cleanRating != "0.0") {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(10.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(cleanRating, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun HomeDownloadCard(
    item: DownloadedItem,
    onClick: () -> Unit,
    isTv: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = Modifier
            .width(130.dp)
            .height(195.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .border(2.dp, if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
            .clip(shape)
            .background(Surface1)
            .historyItemActions(isTv, onClick, null),
        contentAlignment = Alignment.Center
    ) {
        if (!item.coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = item.coverUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color.DarkGray,
                modifier = Modifier.size(32.dp)
            )
        }
        // Épisode : repère saison/épisode en haut à gauche, au même style
        // d'étiquette neutre que le badge de type des favoris. Le titre n'est
        // pas répété sur la vignette, l'affiche suffit à identifier le média.
        val episodeLabel = if (item.type == DownloadedItem.TYPE_EPISODE) {
            EpisodeLabel.format(item.seasonNum, item.episodeNum)
        } else null
        if (episodeLabel != null) {
            HomeMediaTypeBadge(
                label = episodeLabel,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            )
        }
    }
}

@Composable
fun HomeSeriesShowCard(
    stream: SeriesStream,
    onClick: () -> Unit,
    rank: Int? = null,
    onLongClick: (() -> Unit)? = null,
    isTv: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }

    val rankWidth = if (rank == 10) 112.dp else 74.dp
    val cardWidth = if (rank == null) 130.dp else 130.dp + rankWidth - 30.dp
    val posterModifier = Modifier
        .width(130.dp)
        .fillMaxHeight()
        .border(
            width = 2.dp,
            color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
            shape = RoundedCornerShape(14.dp)
        )
        .clip(RoundedCornerShape(14.dp))
        .background(Surface1)

    Box(
        modifier = Modifier
            .width(cardWidth)
            .height(195.dp) // standard 2:3 ratio
            .onFocusChanged { isFocused = it.isFocused }
            .historyItemActions(isTv, onClick, onLongClick),
        contentAlignment = Alignment.Center
    ) {
        rank?.let { TopRankBadge(it, Modifier.align(Alignment.BottomStart)) }

        Box(modifier = posterModifier.align(Alignment.CenterEnd)) {
            if (!stream.cover.isNullOrBlank()) {
                AsyncImage(
                    model = stream.cover,
                    contentDescription = stream.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color.DarkGray,
                    modifier = Modifier.size(32.dp).align(Alignment.Center)
                )
            }

            val cleanRating = stream.rating?.trim()
            if (!cleanRating.isNullOrBlank() && cleanRating != "0" && cleanRating != "0.0") {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(10.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(cleanRating, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopRankBadge(rank: Int, modifier: Modifier = Modifier) {
    val width = if (rank == 10) 112.dp else 74.dp
    val fontSize = if (rank == 10) 104.sp else 138.sp
    Box(modifier = modifier.width(width).height(158.dp)) {
        val text = rank.toString()
        Text(
            text = text,
            color = Color.White,
            fontSize = fontSize,
            lineHeight = fontSize,
            fontWeight = FontWeight.Black,
            fontFamily = BricolageGrotesque,
            style = androidx.compose.ui.text.TextStyle(drawStyle = Stroke(width = 5f)),
            maxLines = 1,
            modifier = Modifier.align(Alignment.BottomStart)
        )
        Text(
            text = text,
            color = Color.Black.copy(alpha = 0.82f),
            fontSize = fontSize,
            lineHeight = fontSize,
            fontWeight = FontWeight.Black,
            fontFamily = BricolageGrotesque,
            maxLines = 1,
            modifier = Modifier.align(Alignment.BottomStart)
        )
    }
}
