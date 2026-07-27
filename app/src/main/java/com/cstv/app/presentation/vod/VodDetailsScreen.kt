package com.cstv.app.presentation.vod

import com.cstv.app.presentation.components.formatReleaseYear
import com.cstv.app.presentation.components.ExpandableText
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
import androidx.compose.material.icons.filled.Replay
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
import com.cstv.app.presentation.theme.AccentLavande
import com.cstv.app.presentation.theme.BricolageGrotesque
import com.cstv.app.presentation.theme.HankenGrotesk
import com.cstv.app.presentation.theme.Surface1
import com.cstv.app.presentation.theme.Surface2
import com.cstv.app.presentation.theme.Surface3
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
import com.cstv.app.domain.model.VodDetails
import com.cstv.app.domain.model.VodStream
import com.cstv.app.domain.model.MediaRatingValue
import com.cstv.app.presentation.components.MediaRatingControls
import com.cstv.app.presentation.components.MediaDetailsTrailerBackdrop
import com.cstv.app.presentation.components.extendUnderTopInset
import com.cstv.app.domain.model.TrailerMedia
import com.cstv.app.presentation.components.TrailerPreviewUiState

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
    onSearchQueryTriggered: (String) -> Unit = {},
    relatedStreams: List<VodStream> = emptyList(),
    onSelectRelated: (VodStream) -> Unit = {},
    downloadItem: com.cstv.app.domain.model.DownloadedItem? = null,
    onDownload: () -> Unit = {},
    onRemoveDownload: () -> Unit = {}
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
    val trailerMedia = remember(details.streamId) { TrailerMedia.Movie(details.streamId) }
    var trailerMuted by remember(trailerMedia) { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(ratingError) { ratingError?.let { snackbarHostState.showSnackbar(it); onConsumeRatingError() } }
    // Hauteur réelle du conteneur : sert à redessiner le décor à l'identique
    // dans le bloc de tête épinglé (voir plus bas).
    var rootHeightPx by remember { mutableIntStateOf(0) }
    val rootHeight = with(LocalDensity.current) { rootHeightPx.toDp() }
    Box(
        modifier = modifier
            .fillMaxSize()
            // Mobile : l'image de tête court jusqu'en haut de la dalle. Sur TV
            // les barres système sont masquées, l'inset y est nul de toute façon.
            .then(if (isTv) Modifier else Modifier.extendUnderTopInset())
            .onSizeChanged { rootHeightPx = it.height }
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
                posterUrl = details.coverBig,
                onPlaybackFailed = onTrailerFailed,
                muted = trailerMuted,
                scrimAlpha = 0.62f,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. Content Column/Scroll
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // Zone de tête, épinglée en haut sur mobile quel que soit le
            // média : l'image occupe toute la largeur sur environ un tiers de
            // la hauteur, et le trailer vient s'y substituer une fois prêt.
            //
            // L'épinglage compense le défilement (graphicsLayer) au lieu de
            // sortir le bloc de la zone défilante : le déplacer dans l'arbre
            // recréerait la WebView le jour où un trailer devient disponible,
            // ce qui relancerait sa lecture depuis la phase de chargement.
            if (isTv) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 24.dp),
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
            } else {
                com.cstv.app.presentation.components.MediaDetailsHeader(
                    imageUrl = details.coverBig,
                    contentDescription = details.name,
                    media = trailerMedia,
                    trailerState = trailerState,
                    muted = trailerMuted,
                    onMutedChange = { trailerMuted = it },
                    onTrailerFailed = onTrailerFailed,
                    onBack = onBack,
                    height = rootHeight * com.cstv.app.presentation.components.MEDIA_DETAILS_HEADER_HEIGHT_FRACTION,
                    modifier = Modifier
                        .zIndex(1f)
                        .graphicsLayer { translationY = scrollState.value.toFloat() }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 20.dp, bottom = 24.dp)
            ) {
            if (isTv) {
                TvLayoutDetails(
                    details = details,
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    onPlayFromBeginning = onPlayFromBeginning,
                    onResumePlayback = onResumePlayback,
                    onSearchQueryTriggered = onSearchQueryTriggered,
                    mediaRating = mediaRating,
                    isRatingSaving = isRatingSaving,
                    onLike = onLike,
                    onDislike = onDislike
                )
            } else {
                MobileLayoutDetails(
                    details = details,
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    onPlayFromBeginning = onPlayFromBeginning,
                    onResumePlayback = onResumePlayback,
                    onSearchQueryTriggered = onSearchQueryTriggered,
                    mediaRating = mediaRating,
                    isRatingSaving = isRatingSaving,
                    onLike = onLike,
                    onDislike = onDislike
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            com.cstv.app.presentation.components.DownloadActionButton(
                item = downloadItem,
                onDownload = onDownload,
                onRemove = onRemoveDownload
            )

            if (!isTv) {
                Spacer(modifier = Modifier.height(12.dp))
                MediaRatingControls(mediaRating, false, onLike, onDislike)
            }

            if (relatedStreams.isNotEmpty()) {
                Spacer(modifier = Modifier.height(28.dp))
                com.cstv.app.presentation.components.RelatedTitlesRow(
                    title = stringResource(R.string.details_related_titles),
                    items = relatedStreams,
                    poster = { it.streamIcon },
                    label = { it.name },
                    onClick = onSelectRelated
                )
            }
            }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
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
    onSearchQueryTriggered: (String) -> Unit,
    mediaRating: MediaRatingValue?,
    isRatingSaving: Boolean,
    onLike: () -> Unit,
    onDislike: () -> Unit
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
                        text = if (isFavorite) "DANS LES FAVORIS" else "AJOUTER FAVORIS",
                        fontWeight = FontWeight.Bold,
                        style = TvTheme.typography.labelSmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            MediaRatingControls(mediaRating, true, onLike, onDislike)
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

            ExpandableText(
                text = details.plot,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLinesCollapsed = 3,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 10.dp))

            // Clickable Credits: Director & Cast
            ClickableCreditsRow(label = "Réalisateur", names = details.director, onClickName = onSearchQueryTriggered)
            Spacer(modifier = Modifier.height(6.dp))
            ClickableCreditsRow(label = "Acteurs", names = details.actors, onClickName = onSearchQueryTriggered)

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
    onSearchQueryTriggered: (String) -> Unit,
    mediaRating: MediaRatingValue?,
    isRatingSaving: Boolean,
    onLike: () -> Unit,
    onDislike: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // L'affiche est rendue par le bloc de tête de l'écran (VodPosterSlot),
        // qui la cède au trailer une fois celui-ci révélé.

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
                    contentDescription = "Favoris",
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

        if (details.isMetadataIncomplete) {
            Text(
                text = "Le serveur n'a pas fourni la fiche de ce titre : seules les " +
                    "informations du catalogue sont affichées. La lecture reste possible.",
                color = Color(0xFFE0B040),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        ExpandableText(
            text = details.plot,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            maxLinesCollapsed = 3,
            textAlign = TextAlign.Justify,
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))

        // Clickable Credits: Director & Cast
        ClickableCreditsRow(label = "Réalisateur", names = details.director, onClickName = onSearchQueryTriggered)
        Spacer(modifier = Modifier.height(8.dp))
        ClickableCreditsRow(label = "Acteurs", names = details.actors, onClickName = onSearchQueryTriggered)

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
                            text = "REPRENDRE LA LECTURE",
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
                    Icon(if (hasHistory) Icons.Default.Replay else Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
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
                        Text(stringResource(R.string.vod_details_resume_playback), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            Button(
                onClick = onPlayFromBeginning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (hasHistory) Color(0xFF2A2A35) else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (hasHistory) Icons.Default.Replay else Icons.Default.PlayArrow,
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
