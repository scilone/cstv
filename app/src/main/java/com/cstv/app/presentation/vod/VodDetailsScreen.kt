package com.cstv.app.presentation.vod
import com.cstv.app.presentation.common.displayLabel

import com.cstv.app.presentation.components.formatReleaseYear
import com.cstv.app.presentation.components.ExpandableText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import com.cstv.app.presentation.theme.BricolageGrotesque
import com.cstv.app.presentation.theme.Surface3
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import com.cstv.app.R
import com.cstv.app.domain.model.VodDetails
import com.cstv.app.domain.model.VodStream
import com.cstv.app.domain.model.MediaRatingValue
import com.cstv.app.presentation.components.MediaRatingControls
import com.cstv.app.domain.model.TrailerMedia
import com.cstv.app.presentation.components.TrailerPreviewUiState

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
    onTrailerFailed: (TrailerMedia) -> Unit = {},
    /** F39 §8.6 (évolution PO) : autres versions de ce film partageant le `linkKey` T21, la version
     *  affichée incluse. Bouton « Versions » masqué si moins de deux entrées. */
    availableVersions: List<VodStream> = emptyList(),
    onSelectVersion: (Int) -> Unit = {},
    ageRating: Int? = null
) {
    val trailerMedia = remember(details.streamId) { TrailerMedia.Movie(details.streamId) }
    // Sur TV le trailer est sonore d'emblée et sans contrôle dédié : la
    // télécommande porte déjà le volume. Sur mobile il reste muet par défaut,
    // avec son bouton dans la tête de fiche.
    var trailerMuted by remember(trailerMedia, isTv) { mutableStateOf(!isTv) }
    val snackbarHostState = remember { SnackbarHostState() }
    // F39 §8.6 (évolution PO) : sélecteur de versions — TV uniquement (dialogue plein écran,
    // `showVersionDialog`) ; mobile l'affiche en dropdown ancré au bouton de lecture, sans passer
    // par cet état. Calculé une seule fois, réutilisé par les deux plateformes.
    var showVersionDialog by remember { mutableStateOf(false) }
    val versionOptions = remember(availableVersions, details.streamId) {
        availableVersions.map { version ->
            com.cstv.app.presentation.player.VersionOption(
                id = version.streamId,
                // Repli sur le titre affiché, jamais un libellé générique inventé
                // ni le nom Xtream brut (version.name).
                label = com.cstv.app.domain.model.mediaVersionSelectorLabel(
                    version.versionLabel, version.cleanTitle.ifBlank { version.name }
                ),
                isActive = version.streamId == details.streamId
            )
        }
    }
    LaunchedEffect(ratingError) { ratingError?.let { snackbarHostState.showSnackbar(it); onConsumeRatingError() } }

    com.cstv.app.presentation.components.TrailerAutoStartEffect(
        media = trailerMedia,
        onContextReady = onTrailerReady,
        onContextEnded = onTrailerEnded
    )

    // L'hôte de snackbar couvre les deux plateformes : posé dans la seule
    // branche mobile, `showSnackbar` restait suspendu indéfiniment sur TV et
    // `onConsumeRatingError` n'était jamais appelé — l'erreur de notation ne
    // s'affichait pas et bloquait toutes les suivantes.
    Box(modifier = modifier.fillMaxSize()) {
        if (isTv) {
            VodDetailsTvLayout(
                details = details,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                onPlayFromBeginning = onPlayFromBeginning,
                onResumePlayback = onResumePlayback,
                onSearchQueryTriggered = onSearchQueryTriggered,
                relatedStreams = relatedStreams,
                onSelectRelated = onSelectRelated,
                mediaRating = mediaRating,
                isRatingSaving = isRatingSaving,
                onLike = onLike,
                onDislike = onDislike,
                trailerState = trailerState,
                onTrailerFailed = onTrailerFailed,
                trailerMuted = trailerMuted,
                hasMultipleVersions = availableVersions.size > 1,
                onOpenVersions = { showVersionDialog = true },
                ageRating = ageRating
            )
        } else {
            // Mobile Layout with vertical scroll
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
            ) {
                val rootHeight = maxHeight

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
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
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

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(top = 20.dp, bottom = 24.dp)
                    ) {
                        MobileLayoutDetails(
                            details = details,
                            isFavorite = isFavorite,
                            onToggleFavorite = onToggleFavorite,
                            onPlayFromBeginning = onPlayFromBeginning,
                            onResumePlayback = onResumePlayback,
                            onSearchQueryTriggered = onSearchQueryTriggered,
                            versionOptions = versionOptions,
                            onSelectVersion = onSelectVersion,
                            ageRating = ageRating
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        com.cstv.app.presentation.components.DownloadActionButton(
                            item = downloadItem,
                            onDownload = onDownload,
                            onRemove = onRemoveDownload
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        MediaRatingControls(mediaRating, false, onLike, onDislike)

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
            }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))

        // F39 §8.6 (évolution PO) : sélectionner une version recharge intégralement la fiche.
        // TV uniquement — mobile sélectionne directement dans le dropdown du bouton de lecture.
        if (showVersionDialog) {
            com.cstv.app.presentation.player.VersionSelectorSheet(
                options = versionOptions,
                isSwitching = false,
                onSelect = { option ->
                    showVersionDialog = false
                    onSelectVersion(option.id)
                },
                onDismiss = { showVersionDialog = false },
                isTv = isTv
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
    versionOptions: List<com.cstv.app.presentation.player.VersionOption> = emptyList(),
    onSelectVersion: (Int) -> Unit = {},
    ageRating: Int? = null
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
                modifier = Modifier
                    .weight(1f, fill = false)
                    .wrapContentWidth(Alignment.Start)
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
            ageRating?.let {
                Text("  •  ", color = Color.DarkGray)
                Text(it.displayLabel(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
        ClickableCreditsRow(
            label = stringResource(R.string.details_credits_director),
            names = details.director,
            onClickName = onSearchQueryTriggered
        )
        Spacer(modifier = Modifier.height(8.dp))
        ClickableCreditsRow(
            label = stringResource(R.string.details_credits_cast),
            names = details.actors,
            onClickName = onSearchQueryTriggered
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Playback Options — F39 §8.6 (évolution PO) : le chevron de version est accolé au bouton
        // « Lire depuis le début », jamais un bouton « Versions » séparé (retour PO), masqué à 0/1
        // candidate (versionOptions vide dans ce cas).
        PlayButtonsRow(
            resumePositionMs = details.resumePositionMs,
            onPlayFromBeginning = onPlayFromBeginning,
            onResumePlayback = { onResumePlayback(details.resumePositionMs) },
            versionOptions = versionOptions,
            onSelectVersion = onSelectVersion
        )
    }
}

@Composable
internal fun ClickableCreditsRow(
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
internal fun CreditNameChip(
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
    versionOptions: List<com.cstv.app.presentation.player.VersionOption> = emptyList(),
    onSelectVersion: (Int) -> Unit = {}
) {
    val hasHistory = resumePositionMs > 0L
    val playContainerColor = if (hasHistory) Color(0xFF2A2A35) else MaterialTheme.colorScheme.primary
    val playText = stringResource(
        if (hasHistory) R.string.vod_details_replay_movie else R.string.vod_details_play_movie
    )
    val playIcon = if (hasHistory) Icons.Default.Replay else Icons.Default.PlayArrow

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
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

        // F39 §8.6 (évolution PO) : chevron de version accolé — jamais un bouton « Versions »
        // séparé. Masqué à 0/1 candidate (retombe alors sur le bouton plein plat d'origine).
        if (versionOptions.size > 1) {
            com.cstv.app.presentation.components.PlayButtonWithVersionsDropdown(
                text = playText,
                icon = playIcon,
                onClick = onPlayFromBeginning,
                containerColor = playContainerColor,
                contentColor = Color.White,
                versions = versionOptions,
                onSelectVersion = onSelectVersion,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Button(
                onClick = onPlayFromBeginning,
                colors = ButtonDefaults.buttonColors(containerColor = playContainerColor),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = playIcon, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = playText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}
