package com.cstv.app.presentation.series

import com.cstv.app.presentation.components.formatReleaseYear
import com.cstv.app.presentation.components.ExpandableText

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
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
import androidx.compose.ui.res.stringResource
import com.cstv.app.R
import com.cstv.app.domain.model.SeriesDetails
import com.cstv.app.domain.model.SeriesStream
import com.cstv.app.presentation.theme.AccentLavande
import com.cstv.app.presentation.theme.BricolageGrotesque
import com.cstv.app.presentation.theme.HankenGrotesk
import com.cstv.app.presentation.theme.Surface1
import com.cstv.app.presentation.theme.Surface2
import com.cstv.app.presentation.theme.Surface3
import com.cstv.app.domain.model.SeriesEpisode
import com.cstv.app.domain.model.SeriesSeason
import com.cstv.app.domain.model.MediaRatingValue
import com.cstv.app.presentation.components.MediaRatingControls
import com.cstv.app.presentation.components.MediaDetailsTrailerBackdrop
import com.cstv.app.domain.model.TrailerMedia
import com.cstv.app.presentation.components.TrailerPreviewUiState

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeriesDetailsScreen(
    details: SeriesDetails,
    isTv: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onEpisodeSelected: (SeriesEpisode) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onSearchQueryTriggered: (String) -> Unit = {},
    relatedSeries: List<SeriesStream> = emptyList(),
    onSelectRelated: (SeriesStream) -> Unit = {},
    episodeDownloads: Map<Int, com.cstv.app.domain.model.DownloadedItem> = emptyMap(),
    onDownloadEpisode: (SeriesEpisode) -> Unit = {},
    onRemoveEpisodeDownload: (Int) -> Unit = {}
    ,mediaRating: MediaRatingValue? = null,
    isRatingSaving: Boolean = false,
    ratingError: String? = null,
    onLike: () -> Unit = {},
    onDislike: () -> Unit = {},
    onConsumeRatingError: () -> Unit = {}
    ,trailerState: TrailerPreviewUiState = TrailerPreviewUiState.Poster,
    onTrailerReady: (TrailerMedia) -> Unit = {},
    onTrailerEnded: () -> Unit = {},
    onTrailerFailed: (TrailerMedia) -> Unit = {}
) {
    val trailerMedia = remember(details.seriesId) { TrailerMedia.Series(details.seriesId) }
    var trailerMuted by remember(trailerMedia) { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(ratingError) { ratingError?.let { snackbarHostState.showSnackbar(it); onConsumeRatingError() } }
    var selectedSeasonNumber by remember { mutableStateOf(details.seasons.firstOrNull()?.seasonNumber ?: 1) }
    val currentEpisodes = remember(selectedSeasonNumber, details.episodes) {
        details.episodes[selectedSeasonNumber] ?: emptyList()
    }

    // Hauteur réelle du conteneur : sert à redessiner le décor à l'identique
    // dans le bloc de tête épinglé (voir plus bas).
    var rootHeightPx by remember { mutableIntStateOf(0) }
    val rootHeight = with(LocalDensity.current) { rootHeightPx.toDp() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { rootHeightPx = it.height }
            .background(if (isTv) Surface1 else Color.Transparent)
    ) {
        // 1. Cinematic Blurred Backdrop Cover Image
        if (!details.cover.isNullOrBlank()) {
            AsyncImage(
                model = details.cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(20.dp)
                    .alpha(0.18f)
            )
        }
        // La résolution démarre dès l'affichage de la fiche ; l'attente perçue
        // est portée par le poster de couverture interne au lecteur.
        com.cstv.app.presentation.components.TrailerAutoStartEffect(
            media = trailerMedia,
            onContextReady = onTrailerReady,
            onContextEnded = onTrailerEnded
        )

        val trailerPlaying = (trailerState as? TrailerPreviewUiState.Playing)?.preview?.media == trailerMedia

        // Sur TV le trailer reste un fond plein écran assombri, derrière un
        // layout en deux colonnes qui ne lui laisse pas d'autre place.
        if (isTv) {
            MediaDetailsTrailerBackdrop(
                media = trailerMedia,
                state = trailerState,
                posterUrl = details.cover,
                onPlaybackFailed = onTrailerFailed,
                muted = trailerMuted,
                scrimAlpha = 0.62f,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. Content Structure
        // Le défilement n'est monté que sur mobile : le layout TV contient des
        // LazyColumn à hauteur pondérée, qui ne peuvent pas être mesurées sous
        // une contrainte de hauteur infinie.
        val scrollState = rememberScrollState()
        // Épinglé dès qu'un trailer est disponible, sans attendre son
        // apparition : basculer en cours de défilement recalait le bloc sous
        // les doigts. La règle est donc binaire à l'arrivée sur la fiche, et
        // ne change plus ensuite. Une fiche sans trailer défile normalement :
        // figer une affiche statique coûterait la moitié de l'écran pour rien.
        val pinHeader = !isTv && trailerPlaying
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (isTv) Modifier else Modifier.verticalScroll(scrollState))
        ) {
            // Zone de tête. Sur mobile, l'image occupe toute la largeur sur
            // environ un tiers de la hauteur, et le trailer vient s'y
            // substituer une fois prêt.
            //
            // L'épinglage compense le défilement au lieu de sortir le bloc de
            // la zone défilante : le déplacer dans l'arbre recréerait la
            // WebView, ce qui relancerait le trailer depuis sa phase de
            // chargement.
            if (isTv) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 24.dp)
                ) {
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
                        if (trailerPlaying) {
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { trailerMuted = !trailerMuted }) {
                                Icon(
                                    if (trailerMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                                    contentDescription = if (trailerMuted) "Activer le son du trailer" else "Couper le son du trailer",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            } else {
                com.cstv.app.presentation.components.MediaDetailsHeader(
                    imageUrl = details.cover,
                    contentDescription = details.name,
                    media = trailerMedia,
                    trailerState = trailerState,
                    muted = trailerMuted,
                    onMutedChange = { trailerMuted = it },
                    onTrailerFailed = onTrailerFailed,
                    onBack = onBack,
                    height = rootHeight * com.cstv.app.presentation.components.MEDIA_DETAILS_HEADER_HEIGHT_FRACTION,
                    modifier = Modifier
                        .zIndex(if (pinHeader) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (pinHeader) scrollState.value.toFloat() else 0f
                        }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Sur TV, sans défilement, c'est ce bloc qui doit borner la
                    // hauteur laissée aux listes d'épisodes.
                    .then(if (isTv) Modifier.weight(1f) else Modifier)
                    .padding(horizontal = 24.dp)
                    .padding(top = if (isTv) 0.dp else 20.dp, bottom = 24.dp)
            ) {
            if (isTv) {
                TvLayout(
                    details = details,
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    selectedSeason = selectedSeasonNumber,
                    episodes = currentEpisodes,
                    onSeasonSelected = { selectedSeasonNumber = it },
                    onEpisodeClick = onEpisodeSelected,
                    onSearchQueryTriggered = onSearchQueryTriggered,
                    relatedSeries = relatedSeries,
                    onSelectRelated = onSelectRelated,
                    episodeDownloads = episodeDownloads,
                    onDownloadEpisode = onDownloadEpisode,
                    onRemoveEpisodeDownload = onRemoveEpisodeDownload,
                    mediaRating = mediaRating,
                    isRatingSaving = isRatingSaving,
                    onLike = onLike,
                    onDislike = onDislike
                )
            } else {
                MobileLayout(
                    details = details,
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    selectedSeason = selectedSeasonNumber,
                    episodes = currentEpisodes,
                    onSeasonSelected = { selectedSeasonNumber = it },
                    onEpisodeClick = onEpisodeSelected,
                    onSearchQueryTriggered = onSearchQueryTriggered,
                    relatedSeries = relatedSeries,
                    onSelectRelated = onSelectRelated,
                    episodeDownloads = episodeDownloads,
                    onDownloadEpisode = onDownloadEpisode,
                    onRemoveEpisodeDownload = onRemoveEpisodeDownload,
                    mediaRating = mediaRating,
                    isRatingSaving = isRatingSaving,
                    onLike = onLike,
                    onDislike = onDislike
                )
            }
            }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvLayout(
    details: SeriesDetails,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    selectedSeason: Int,
    episodes: List<SeriesEpisode>,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeClick: (SeriesEpisode) -> Unit,
    onSearchQueryTriggered: (String) -> Unit,
    relatedSeries: List<SeriesStream> = emptyList(),
    onSelectRelated: (SeriesStream) -> Unit = {},
    episodeDownloads: Map<Int, com.cstv.app.domain.model.DownloadedItem> = emptyMap(),
    onDownloadEpisode: (SeriesEpisode) -> Unit = {},
    onRemoveEpisodeDownload: (Int) -> Unit = {},
    mediaRating: MediaRatingValue?,
    isRatingSaving: Boolean,
    onLike: () -> Unit,
    onDislike: () -> Unit
) {
    val allEpisodes = details.episodes.values.flatten()
    val incompleteEpisodes = allEpisodes.filter { ep ->
        ep.resumePositionMs > 1000L && (ep.durationMs <= 0L || ep.resumePositionMs < ep.durationMs - 5000L)
    }
    val resumeEpisode = incompleteEpisodes.maxByOrNull { it.lastAccessedAt }
    
    val firstSeasonNum = details.seasons.firstOrNull()?.seasonNumber
    val firstEpisode = firstSeasonNum?.let { details.episodes[it]?.firstOrNull() } ?: allEpisodes.firstOrNull()
    val targetEpisode = resumeEpisode ?: firstEpisode
    val hasResume = resumeEpisode != null

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        // Left Column: Poster Cover & Season Selection list & Favorite Button
        Column(modifier = Modifier.width(240.dp)) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color.DarkGray, RoundedCornerShape(16.dp))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Surface3),
                    contentAlignment = Alignment.Center
                ) {
                    if (!details.cover.isNullOrBlank()) {
                        AsyncImage(
                            model = details.cover,
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
                        text = if (isFavorite) "DANS LES FAVORIS" else "AJOUTER FAVORIS",
                        fontWeight = FontWeight.Bold,
                        style = TvTheme.typography.labelSmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            MediaRatingControls(mediaRating, true, onLike, onDislike)

            Spacer(modifier = Modifier.height(12.dp))

            Text(stringResource(R.string.series_details_seasons_label), color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(details.seasons) { season ->
                    val isSelected = season.seasonNumber == selectedSeason
                    var isFocused by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .onFocusChanged { 
                                isFocused = it.isFocused
                                if (it.isFocused) onSeasonSelected(season.seasonNumber)
                            }
                            .background(
                                when {
                                    isFocused -> MaterialTheme.colorScheme.primary
                                    isSelected -> Color(0xFF2C2C35)
                                    else -> Color.Transparent
                                }
                            )
                            .clickable { onSeasonSelected(season.seasonNumber) }
                            .padding(10.dp)
                    ) {
                        Text(
                            text = season.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Right Column: Show Description and Season Episode list
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(details.name.uppercase(), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Metadata row: Year, Genre, Rating
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(formatReleaseYear(details.releaseDate).ifBlank { "Inconnu" }, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("|", color = Color.DarkGray)
                    Text(
                        text = details.genre ?: "Inconnu",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text("|", color = Color.DarkGray)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(details.rating ?: "0.0", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Plot
                ExpandableText(
                    text = details.plot ?: "Aucun résumé disponible.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLinesCollapsed = 3,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 10.dp))

                // Clickable Credits: Director & Cast
                ClickableCreditsRow(label = "Réalisateur", names = details.director ?: "Inconnu", onClickName = onSearchQueryTriggered)
                Spacer(modifier = Modifier.height(6.dp))
                ClickableCreditsRow(label = "Acteurs", names = details.actors ?: "Inconnu", onClickName = onSearchQueryTriggered)

                Spacer(modifier = Modifier.height(16.dp))

                // Playback Resume / Play button
                if (targetEpisode != null) {
                    var isFocusedBtn by remember { mutableStateOf(false) }
                    TvButton(
                        onClick = { onEpisodeClick(targetEpisode) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .onFocusChanged { isFocusedBtn = it.isFocused }
                            .border(width = 2.dp, color = if (isFocusedBtn) Color.Yellow else Color.Transparent, shape = RoundedCornerShape(8.dp))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            TvText(
                                text = if (hasResume) {
                                    "REPRENDRE : " + com.cstv.app.domain.model.EpisodeLabel
                                        .format(resumeEpisode!!.seasonNum, resumeEpisode.episodeNum)
                                        .orEmpty()
                                } else "LIRE LA SÉRIE",
                                fontWeight = FontWeight.Bold,
                                style = TvTheme.typography.labelMedium,
                                color = Color.White
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Text(
                    text = "ÉPISODES",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (episodes.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.series_details_no_episodes_for_season), color = Color.Gray)
                    }
                }
            } else {
                items(episodes) { episode ->
                    EpisodeCardItem(
                        episode = episode,
                        onClick = { onEpisodeClick(episode) },
                        download = episodeDownloads[episode.id],
                        onDownload = { onDownloadEpisode(episode) },
                        onRemoveDownload = { onRemoveEpisodeDownload(episode.id) }
                    )
                }
            }

            if (relatedSeries.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(28.dp))
                    com.cstv.app.presentation.components.RelatedTitlesRow(
                        title = stringResource(R.string.details_related_titles),
                        items = relatedSeries,
                        poster = { it.cover },
                        label = { it.name },
                        onClick = onSelectRelated
                    )
                }
            }
        }
    }
}

@Composable
private fun MobileLayout(
    details: SeriesDetails,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    selectedSeason: Int,
    episodes: List<SeriesEpisode>,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeClick: (SeriesEpisode) -> Unit,
    onSearchQueryTriggered: (String) -> Unit,
    relatedSeries: List<SeriesStream> = emptyList(),
    onSelectRelated: (SeriesStream) -> Unit = {},
    episodeDownloads: Map<Int, com.cstv.app.domain.model.DownloadedItem> = emptyMap(),
    onDownloadEpisode: (SeriesEpisode) -> Unit = {},
    onRemoveEpisodeDownload: (Int) -> Unit = {},
    mediaRating: MediaRatingValue?,
    isRatingSaving: Boolean,
    onLike: () -> Unit,
    onDislike: () -> Unit
) {
    val allEpisodes = details.episodes.values.flatten()
    val incompleteEpisodes = allEpisodes.filter { ep ->
        ep.resumePositionMs > 1000L && (ep.durationMs <= 0L || ep.resumePositionMs < ep.durationMs - 5000L)
    }
    val resumeEpisode = incompleteEpisodes.maxByOrNull { it.lastAccessedAt }

    val firstSeasonNum = details.seasons.firstOrNull()?.seasonNumber
    val firstEpisode = firstSeasonNum?.let { details.episodes[it]?.firstOrNull() } ?: allEpisodes.firstOrNull()
    val targetEpisode = resumeEpisode ?: firstEpisode
    val hasResume = resumeEpisode != null

    // Le défilement est porté par l'écran, qui englobe aussi la zone de tête :
    // le trailer doit défiler avec l'image qu'il remplace.
    Column(modifier = Modifier.fillMaxWidth()) {
        // L'image est rendue par la zone de tête de l'écran
        // (MediaDetailsHeader), qui la cède au trailer une fois celui-ci prêt.

        // Title & Favorite row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
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
                    contentDescription = "Favori",
                    tint = if (isFavorite) Color.Yellow else Color.DarkGray,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Metadata row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            Text(formatReleaseYear(details.releaseDate).ifBlank { "Inconnu" }, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("  •  ", color = Color.DarkGray)
            Text(
                text = details.genre ?: "Inconnu",
                color = Color.LightGray,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text("  •  ", color = Color.DarkGray)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text(details.rating ?: "0.0", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        // Plot
        ExpandableText(
            text = details.plot ?: "Aucun résumé disponible.",
            fontSize = 13.sp,
            lineHeight = 18.sp,
            maxLinesCollapsed = 3,
            textAlign = TextAlign.Justify,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        )

        HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

        // Clickable Credits: Director & Cast
        ClickableCreditsRow(label = "Réalisateur", names = details.director ?: "Inconnu", onClickName = onSearchQueryTriggered)
        Spacer(modifier = Modifier.height(8.dp))
        ClickableCreditsRow(label = "Acteurs", names = details.actors ?: "Inconnu", onClickName = onSearchQueryTriggered)

        Spacer(modifier = Modifier.height(24.dp))

        // Playback resume button
        if (targetEpisode != null) {
            Button(
                onClick = { onEpisodeClick(targetEpisode) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (hasResume) {
                            "REPRENDRE : " + com.cstv.app.domain.model.EpisodeLabel
                                .format(resumeEpisode!!.seasonNum, resumeEpisode.episodeNum)
                                .orEmpty()
                        } else "LIRE LA SÉRIE",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        MediaRatingControls(mediaRating, false, onLike, onDislike)
        Spacer(modifier = Modifier.height(20.dp))

        // Seasons lazy row
        Text(
            text = "SAISONS",
            color = Color.Gray,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            items(details.seasons) { season ->
                val isSelected = season.seasonNumber == selectedSeason
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF2A2A35))
                        .clickable { onSeasonSelected(season.seasonNumber) }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = season.name,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // List of episodes
        Text(
            text = "ÉPISODES",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (episodes.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.series_details_no_episodes), color = Color.Gray)
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            ) {
                episodes.forEach { episode ->
                    EpisodeCardItem(
                        episode = episode,
                        onClick = { onEpisodeClick(episode) },
                        download = episodeDownloads[episode.id],
                        onDownload = { onDownloadEpisode(episode) },
                        onRemoveDownload = { onRemoveEpisodeDownload(episode.id) }
                    )
                }
            }
        }

        if (relatedSeries.isNotEmpty()) {
            Spacer(modifier = Modifier.height(28.dp))
            com.cstv.app.presentation.components.RelatedTitlesRow(
                title = stringResource(R.string.details_related_titles),
                items = relatedSeries,
                poster = { it.cover },
                label = { it.name },
                onClick = onSelectRelated
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ClickableCreditsRow(
    label: String,
    names: String,
    onClickName: (String) -> Unit
) {
    if (names.isBlank() || names == "Inconnu") return

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
            .background(if (isFocused) Color(0xFF2C2C35) else Surface3)
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
private fun EpisodeDownloadControl(
    download: com.cstv.app.domain.model.DownloadedItem?,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit
) {
    val status = download?.status
    when (status) {
        null -> IconButton(onClick = onDownload, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Download, contentDescription = "Télécharger", tint = Color.LightGray, modifier = Modifier.size(20.dp))
        }
        com.cstv.app.domain.model.DownloadStatus.COMPLETED -> IconButton(onClick = onRemoveDownload, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.CheckCircle, contentDescription = "Supprimer le téléchargement", tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
        }
        com.cstv.app.domain.model.DownloadStatus.FAILED -> IconButton(onClick = onDownload, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.ErrorOutline, contentDescription = "Réessayer", tint = Color(0xFFCF6679), modifier = Modifier.size(20.dp))
        }
        else -> IconButton(onClick = onRemoveDownload, modifier = Modifier.size(32.dp)) {
            // En cours / en attente / pause : anneau de progression, tap = annuler.
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(20.dp)) {
                CircularProgressIndicator(
                    progress = { (download.percent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.DarkGray
                )
            }
        }
    }
}

@Composable
private fun EpisodeCardItem(
    episode: SeriesEpisode,
    onClick: () -> Unit,
    download: com.cstv.app.domain.model.DownloadedItem? = null,
    onDownload: () -> Unit = {},
    onRemoveDownload: () -> Unit = {}
) {
    var isFocused by remember { mutableStateOf(false) }
    val hasProgress = episode.resumePositionMs > 0 && episode.durationMs > 0
    val progressFraction = if (hasProgress) episode.resumePositionMs.toFloat() / episode.durationMs.toFloat() else 0f

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) Color(0xFF23232D) else Surface3
        ),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = 2.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Episode thumbnail image (movieImage)
                Box(
                    modifier = Modifier
                        .size(width = 80.dp, height = 50.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Surface1),
                    contentAlignment = Alignment.Center
                ) {
                    if (!episode.movieImage.isNullOrBlank()) {
                        AsyncImage(
                            model = episode.movieImage,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
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
                        text = "ÉPISODE ${episode.episodeNum}",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Text(
                        text = episode.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(episode.duration, color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    EpisodeDownloadControl(
                        download = download,
                        onDownload = onDownload,
                        onRemoveDownload = onRemoveDownload
                    )
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                }
            }

            if (episode.plot.isNotBlank() && episode.plot != "Aucun résumé disponible.") {
                Spacer(modifier = Modifier.height(6.dp))
                ExpandableText(
                    text = episode.plot,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLinesCollapsed = 2
                )
            }

            // Visual Progress Bar (Resume / déjà vu indicator)
            if (hasProgress) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        progress = progressFraction,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.DarkGray,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "En cours de lecture (reprendre)",
                        color = Color.Gray,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Light
                    )
                }
            }
        }
    }
}
