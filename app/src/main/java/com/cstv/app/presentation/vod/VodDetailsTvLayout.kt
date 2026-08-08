package com.cstv.app.presentation.vod

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cstv.app.R
import com.cstv.app.domain.model.MediaRatingValue
import com.cstv.app.domain.model.TrailerMedia
import com.cstv.app.domain.model.VodDetails
import com.cstv.app.domain.model.VodStream
import com.cstv.app.presentation.components.MediaDetailsTrailerBackdrop
import com.cstv.app.presentation.components.RelatedTitlesRow
import com.cstv.app.presentation.components.TrailerPreviewUiState
import com.cstv.app.presentation.components.formatReleaseYear
import com.cstv.app.presentation.components.rememberTvInitialFocus
import com.cstv.app.presentation.components.tvInitialFocusTarget
import com.cstv.app.presentation.theme.AccentLavande
import com.cstv.app.presentation.theme.AccentLavandeHover
import com.cstv.app.presentation.theme.BricolageGrotesque
import com.cstv.app.presentation.theme.FavoriteGold
import com.cstv.app.presentation.theme.HankenGrotesk
import com.cstv.app.presentation.theme.RatingDislike
import com.cstv.app.presentation.theme.RatingLike
import com.cstv.app.presentation.theme.Surface1
import com.cstv.app.presentation.theme.Surface3
import com.cstv.app.presentation.theme.TextPrimary
import com.cstv.app.presentation.theme.TextSecondary

/** Réserve laissée en bas d'écran par le bloc principal : la rangée « Titres associés » y dépasse. */
private val TV_DETAILS_RELATED_PEEK = 110.dp

/** Marge conservée sous la rangée une fois celle-ci entièrement remontée. */
private val TV_DETAILS_BOTTOM_RESERVE = 24.dp

/** Filets et séparateurs : une variante d'opacité de [TextSecondary], jamais une couleur neuve. */
private val TvDetailsDividerColor = TextSecondary.copy(alpha = 0.35f)

/**
 * Pure function to calculate the vertical shift in pixels required to reveal
 * the related titles block entirely on screen.
 */
fun tvDetailsRelatedShiftPx(
    mainBlockHeight: Float,
    relatedRowHeight: Float,
    bottomReserve: Float,
    screenHeight: Float
): Float {
    if (relatedRowHeight <= 0f) return 0f
    return (mainBlockHeight + relatedRowHeight + bottomReserve - screenHeight).coerceAtLeast(0f)
}

@Composable
fun VodDetailsTvLayout(
    details: VodDetails,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onPlayFromBeginning: () -> Unit,
    onResumePlayback: (Long) -> Unit,
    onSearchQueryTriggered: (String) -> Unit,
    relatedStreams: List<VodStream>,
    onSelectRelated: (VodStream) -> Unit,
    mediaRating: MediaRatingValue?,
    isRatingSaving: Boolean,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    trailerState: TrailerPreviewUiState,
    onTrailerFailed: (TrailerMedia) -> Unit,
    trailerMuted: Boolean,
    modifier: Modifier = Modifier
) {
    val trailerMedia = remember(details.streamId) { TrailerMedia.Movie(details.streamId) }
    val trailerPlaying = (trailerState as? TrailerPreviewUiState.Playing)?.preview?.media == trailerMedia

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Surface1)
    ) {
        val screenHeightDp = maxHeight
        val density = LocalDensity.current
        val bottomReservePx = with(density) { TV_DETAILS_BOTTOM_RESERVE.toPx() }
        val screenHeightPx = with(density) { screenHeightDp.toPx() }

        // 1. Trailer full screen backdrop OR left-side big poster with fade
        if (trailerPlaying) {
            MediaDetailsTrailerBackdrop(
                media = trailerMedia,
                state = trailerState,
                posterUrl = details.coverBig,
                onPlaybackFailed = onTrailerFailed,
                muted = trailerMuted,
                scrimAlpha = 0.62f,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.45f)
            ) {
                if (!details.coverBig.isNullOrBlank()) {
                    AsyncImage(
                        model = details.coverBig,
                        contentDescription = details.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Seamless horizontal fade from transparent (left) to Surface1 (right)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.horizontalGradient(
                                    0.2f to Color.Transparent,
                                    1.0f to Surface1
                                )
                            )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Surface3),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(54.dp)
                        )
                    }
                }
            }
        }

        // TV initial focus management for play buttons
        val hasHistory = details.resumePositionMs > 0L
        val focusState = rememberTvInitialFocus(
            isTv = true,
            ready = true,
            targetKey = details.streamId
        )

        var mainBlockHeightPx by remember { mutableFloatStateOf(0f) }
        var relatedRowHeightPx by remember { mutableFloatStateOf(0f) }
        var isRelatedFocused by remember { mutableStateOf(false) }

        // Animate the vertical shift when focus moves to related titles
        val targetShift = if (isRelatedFocused) {
            tvDetailsRelatedShiftPx(
                mainBlockHeight = mainBlockHeightPx,
                relatedRowHeight = relatedRowHeightPx,
                bottomReserve = bottomReservePx,
                screenHeight = screenHeightPx
            )
        } else {
            0f
        }

        val animatedShift by animateFloatAsState(
            targetValue = targetShift,
            animationSpec = tween(durationMillis = 300),
            label = "detailsShift"
        )

        // Main shiftable content.
        //
        // `wrapContentHeight(unbounded = true)` est indispensable : un `Column`
        // mesure ses enfants non pondérés avec « l'espace restant » en hauteur
        // maximale. Le bloc principal occupant `écran - PEEK`, la rangée
        // « Titres associés » se voyait plafonnée à PEEK (110 dp) — vignettes
        // clippées, et hauteur mesurée fausse donc remontée quasi nulle.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .graphicsLayer {
                    this.translationY = -animatedShift
                }
        ) {
            // Main Details Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(screenHeightDp - TV_DETAILS_RELATED_PEEK)
                    .onSizeChanged { mainBlockHeightPx = it.height.toFloat() }
            ) {
                // Reserve left space for the poster/artwork
                Spacer(modifier = Modifier.fillMaxWidth(0.45f))

                // Movie Information Column (Right)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 48.dp, top = 36.dp, bottom = 12.dp)
                ) {
                    // Movie Title
                    Text(
                        text = details.name.uppercase(),
                        color = TextPrimary,
                        fontSize = 38.sp,
                        fontFamily = BricolageGrotesque,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Metadata Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = formatReleaseYear(details.releaseDate),
                            color = AccentLavande,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            fontFamily = HankenGrotesk
                        )
                        MetadataSeparator()
                        Text(
                            text = details.genre,
                            color = TextSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                            fontFamily = HankenGrotesk
                        )
                        details.duration?.let { dur ->
                            MetadataSeparator()
                            Text(
                                text = dur,
                                color = TextSecondary,
                                fontSize = 13.sp,
                                fontFamily = HankenGrotesk
                            )
                        }
                        if (details.rating.isNotBlank() && details.rating != "0" && details.rating != "0.0") {
                            MetadataSeparator()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = FavoriteGold,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = details.rating,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    fontFamily = HankenGrotesk
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Plot/Synopsis
                    Text(
                        text = details.plot,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                        color = TextSecondary,
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp,
                        fontFamily = HankenGrotesk,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    HorizontalDivider(
                        color = TvDetailsDividerColor,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    // Credits (Director & Cast)
                    ClickableCreditsRow(
                        label = stringResource(R.string.details_credits_director),
                        names = details.director,
                        onClickName = onSearchQueryTriggered
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ClickableCreditsRow(
                        label = stringResource(R.string.details_credits_cast),
                        names = details.actors,
                        onClickName = onSearchQueryTriggered
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Integrated Actions Row: Favorite / Like / Dislike
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DetailActionButton(
                            icon = Icons.Default.Star,
                            text = if (isFavorite) {
                                stringResource(R.string.vod_details_remove_favorite)
                            } else {
                                stringResource(R.string.vod_details_add_favorite)
                            },
                            tint = if (isFavorite) FavoriteGold else TextPrimary,
                            selected = isFavorite,
                            onClick = onToggleFavorite
                        )

                        ActionSeparator()

                        DetailActionButton(
                            icon = Icons.Default.ThumbUp,
                            text = stringResource(R.string.media_rating_like),
                            tint = if (mediaRating == MediaRatingValue.LIKE) RatingLike else TextPrimary,
                            selected = mediaRating == MediaRatingValue.LIKE,
                            enabled = !isRatingSaving,
                            onClick = onLike
                        )

                        ActionSeparator()

                        DetailActionButton(
                            icon = Icons.Default.ThumbDown,
                            text = stringResource(R.string.media_rating_dislike),
                            tint = if (mediaRating == MediaRatingValue.DISLIKE) RatingDislike else TextPrimary,
                            selected = mediaRating == MediaRatingValue.DISLIKE,
                            enabled = !isRatingSaving,
                            onClick = onDislike
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Playback options with high quality pill-shaped TV buttons (no icons)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.width(360.dp)
                    ) {
                        if (hasHistory) {
                            PlayButton(
                                text = stringResource(R.string.vod_details_resume_playback),
                                onClick = { onResumePlayback(details.resumePositionMs) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tvInitialFocusTarget(focusState)
                            )
                            PlayButton(
                                text = stringResource(R.string.vod_details_replay_movie),
                                onClick = onPlayFromBeginning,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            PlayButton(
                                text = stringResource(R.string.vod_details_play_movie),
                                onClick = onPlayFromBeginning,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tvInitialFocusTarget(focusState)
                            )
                        }
                    }
                }
            }

            // Related Titles Block (bottom section)
            if (relatedStreams.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { relatedRowHeightPx = it.height.toFloat() }
                        .onFocusChanged { isRelatedFocused = it.hasFocus }
                ) {
                    RelatedTitlesRow(
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

/** Filet vertical entre deux actions de la rangée favoris / j'aime / je n'aime pas. */
@Composable
private fun ActionSeparator() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(16.dp)
            .background(TvDetailsDividerColor)
    )
}

/** Barre verticale « | » de la ligne de métadonnées. */
@Composable
private fun MetadataSeparator() {
    Text(text = "|", color = TvDetailsDividerColor, fontSize = 14.sp)
}

@Composable
private fun DetailActionButton(
    icon: ImageVector,
    text: String,
    tint: Color,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    var isFocused by remember { mutableStateOf(false) }
    val description = stringResource(
        if (selected) R.string.media_rating_selected_description else R.string.media_rating_action_description,
        text
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .background(if (isFocused) AccentLavande.copy(alpha = 0.15f) else Color.Transparent)
            .border(
                width = 1.5.dp,
                color = if (isFocused) AccentLavande else Color.Transparent,
                shape = RoundedCornerShape(24.dp)
            )
            .clickable(enabled = enabled) { onClick() }
            .semantics { contentDescription = description }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                color = if (isFocused) AccentLavande else TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp,
                fontFamily = HankenGrotesk
            )
        }
    }
}

/**
 * Bouton de lecture : pilule pleine largeur, sans icône.
 *
 * Volontairement construit sur `Box` + `clickable` plutôt que sur
 * `androidx.tv.material3.Button` : ce dernier compose sa propre `Surface`, qui
 * recouvre tout fond posé par modificateur et se colore avec le jeu de couleurs
 * par défaut de tv-material3 — l'application n'installant pas de
 * `androidx.tv.material3.MaterialTheme`, la charte n'était pas respectée. C'est
 * aussi le motif déjà employé par `CreditNameChip` et `RelatedTitleCard`.
 */
@Composable
private fun PlayButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(24.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .background(
                color = if (isFocused) AccentLavande else Surface3,
                shape = RoundedCornerShape(24.dp)
            )
            .border(
                width = 2.dp,
                color = if (isFocused) AccentLavandeHover else Color.Transparent,
                shape = RoundedCornerShape(24.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isFocused) Surface1 else TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            fontFamily = HankenGrotesk
        )
    }
}
