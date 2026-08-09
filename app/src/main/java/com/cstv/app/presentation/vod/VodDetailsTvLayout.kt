package com.cstv.app.presentation.vod

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.onFocusedBoundsChanged
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.cstv.app.R
import com.cstv.app.domain.model.MediaRatingValue
import com.cstv.app.domain.model.TrailerMedia
import com.cstv.app.domain.model.VodDetails
import com.cstv.app.domain.model.VodStream
import com.cstv.app.presentation.components.LocalTvFocusSelector
import com.cstv.app.presentation.components.MediaDetailsTrailerBackdrop
import com.cstv.app.presentation.components.RelatedTitlesRow
import com.cstv.app.presentation.components.TrailerPreviewUiState
import com.cstv.app.presentation.components.TvFocusSelectorOverlay
import com.cstv.app.presentation.components.TvFocusSelectorState
import com.cstv.app.presentation.components.formatReleaseYear
import com.cstv.app.presentation.components.rememberTvInitialFocus
import com.cstv.app.presentation.components.tvFocusDownTo
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
private val TV_DETAILS_RELATED_PEEK = 96.dp

/** Marge conservée sous la rangée une fois celle-ci entièrement remontée. */
private val TV_DETAILS_BOTTOM_RESERVE = 24.dp

/** Retrait latéral de la rangée « Titres associés », qui touchait les bords. */
private val TV_DETAILS_RELATED_SIDE_MARGIN = 48.dp

/** Rayon du cadre de focus, aligné sur celui des vignettes de la rangée. */
private val TV_DETAILS_SELECTOR_RADIUS = 12.dp

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

@OptIn(ExperimentalFoundationApi::class)
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

        // 1. Panneau gauche : affiche plein bord, ou trailer à sa place.
        //
        // Le trailer occupe l'emplacement de l'affiche et non plus le fond de
        // toute la page : la maquette réserve la moitié gauche à l'image, et un
        // fond plein écran passait la colonne de texte par-dessus la vidéo.
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.45f)
        ) {
            when {
                trailerPlaying -> MediaDetailsTrailerBackdrop(
                    media = trailerMedia,
                    state = trailerState,
                    posterUrl = details.coverBig,
                    onPlaybackFailed = onTrailerFailed,
                    muted = trailerMuted,
                    modifier = Modifier.fillMaxSize()
                )

                !details.coverBig.isNullOrBlank() -> AsyncImage(
                    model = details.coverBig,
                    contentDescription = details.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                else -> Box(
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

            // Fondu horizontal vers Surface1, posé aussi bien sur l'affiche que
            // sur le trailer pour que la jonction avec la colonne de droite
            // reste la même dans les deux cas.
            if (trailerPlaying || !details.coverBig.isNullOrBlank()) {
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
        var showFullPlot by remember(details.streamId) { mutableStateOf(false) }

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

        // Couche avant du focus (F23) pour la rangée de titres associés : le
        // cadre reste fixe sur l'emplacement de la première vignette et c'est
        // la rangée qui défile dessous, comme partout ailleurs sur TV.
        val focusSelector = remember { TvFocusSelectorState() }
        var focusedRelatedChild by remember { mutableStateOf<LayoutCoordinates?>(null) }

        // Descente explicite vers la première affiche. Le bouton de lecture
        // occupe toute la largeur de la colonne : son centre tombe vers 72 % de
        // l'écran, et la recherche de focus par défaut — qui retient le
        // candidat géométriquement le plus proche — atterrissait donc sur la
        // sixième vignette, que le pivot ramenait ensuite à l'ancre. D'où un
        // défilement de six affiches à chaque ouverture du bloc (B22).
        val firstRelatedFocus = remember(details.streamId) { FocusRequester() }

        // La rangée revient à son début dès que le focus quitte le bloc.
        //
        // Sans cela, la descente suivante retombait dans le défaut d'origine :
        // une rangée laissée défilée n'a plus sa première vignette composée,
        // `requestFocus` échoue donc silencieusement, et Compose reprend sa
        // recherche géométrique — qui vise de nouveau le milieu de l'écran.
        val relatedRowState = rememberLazyListState()
        LaunchedEffect(isRelatedFocused) {
            if (!isRelatedFocused) relatedRowState.scrollToItem(0)
        }

        // Le cadre est dessiné à une position *publiée*, alors que la remontée
        // translate la colonne entière : sans republication, il resterait à sa
        // place d'avant la remontée. On le repose donc à chaque valeur de
        // l'animation, à partir de `positionInRoot()` et de la taille — jamais
        // de `boundsInRoot()`, qui clippe par les parents et rendrait un
        // rectangle vide pour une vignette encore hors champ (B22).
        LaunchedEffect(focusSelector) {
            snapshotFlow { animatedShift }.collect {
                val coordinates = focusedRelatedChild?.takeIf { c -> c.isAttached } ?: return@collect
                focusSelector.publishStabilised(
                    bounds = Rect(coordinates.positionInRoot(), coordinates.size.toSize()),
                    cornerRadius = TV_DETAILS_SELECTOR_RADIUS
                )
            }
        }

        // Main shiftable content.
        //
        // `wrapContentHeight(unbounded = true)` est indispensable : un `Column`
        // mesure ses enfants non pondérés avec « l'espace restant » en hauteur
        // maximale. Le bloc principal occupant `écran - PEEK`, la rangée
        // « Titres associés » se voyait plafonnée à PEEK (110 dp) — vignettes
        // clippées, et hauteur mesurée fausse donc remontée quasi nulle.
        CompositionLocalProvider(LocalTvFocusSelector provides focusSelector) {
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
                        .padding(end = 48.dp, top = 20.dp, bottom = 12.dp)
                ) {
                    // Movie Title
                    Text(
                        text = details.name.uppercase(),
                        color = TextPrimary,
                        fontSize = 30.sp,
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

                    Spacer(modifier = Modifier.height(10.dp))

                    // Synopsis : pondéré, donc mesuré avec la place qui reste
                    // une fois les crédits, les actions et les boutons posés.
                    // Un texte long ne peut plus les chasser hors de l'écran.
                    var plotOverflows by remember(details.streamId) { mutableStateOf(false) }
                    Text(
                        text = details.plot,
                        overflow = TextOverflow.Ellipsis,
                        color = TextSecondary,
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp,
                        fontFamily = HankenGrotesk,
                        onTextLayout = { plotOverflows = it.hasVisualOverflow },
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(bottom = 4.dp)
                    )

                    if (plotOverflows) {
                        PlotMoreLink(onClick = { showFullPlot = true })
                    }

                    HorizontalDivider(
                        color = TvDetailsDividerColor,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(vertical = 6.dp)
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
                    //
                    // Les trois actions se partagent la largeur à parts égales
                    // (`weight`) au lieu de se serrer à gauche sur environ trois
                    // quarts du bloc : la rangée s'aligne ainsi sur les boutons
                    // de lecture et le synopsis, qui l'occupent en entier. Le
                    // cadre de focus couvre du coup tout le tiers, ce qui le
                    // rend nettement plus lisible de loin.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
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
                            onClick = onToggleFavorite,
                            modifier = Modifier.weight(1f)
                        )

                        ActionSeparator()

                        DetailActionButton(
                            icon = Icons.Default.ThumbUp,
                            text = stringResource(R.string.media_rating_like),
                            tint = if (mediaRating == MediaRatingValue.LIKE) RatingLike else TextPrimary,
                            selected = mediaRating == MediaRatingValue.LIKE,
                            enabled = !isRatingSaving,
                            onClick = onLike,
                            modifier = Modifier.weight(1f)
                        )

                        ActionSeparator()

                        DetailActionButton(
                            icon = Icons.Default.ThumbDown,
                            text = stringResource(R.string.media_rating_dislike),
                            tint = if (mediaRating == MediaRatingValue.DISLIKE) RatingDislike else TextPrimary,
                            selected = mediaRating == MediaRatingValue.DISLIKE,
                            enabled = !isRatingSaving,
                            onClick = onDislike,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Boutons de lecture, au dessin de la fiche mobile et sur
                    // toute la largeur de la colonne. Comme sur mobile, une
                    // position de reprise fait apparaître le doublon
                    // « relire depuis le début » en action secondaire.
                    // La descente explicite vers les titres associés est portée
                    // par le **dernier** bouton, jamais par la colonne : posée
                    // sur celle-ci, elle interceptait la descente depuis
                    // « reprendre la lecture » et sautait par-dessus « relire
                    // depuis le début ».
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (hasHistory) {
                            PlayButton(
                                text = stringResource(R.string.vod_details_resume_playback),
                                icon = Icons.Default.PlayArrow,
                                onClick = { onResumePlayback(details.resumePositionMs) },
                                primary = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tvInitialFocusTarget(focusState)
                            )
                            PlayButton(
                                text = stringResource(R.string.vod_details_replay_movie),
                                icon = Icons.Default.Replay,
                                onClick = onPlayFromBeginning,
                                primary = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tvFocusDownTo(firstRelatedFocus)
                            )
                        } else {
                            PlayButton(
                                text = stringResource(R.string.vod_details_play_movie),
                                icon = Icons.Default.PlayArrow,
                                onClick = onPlayFromBeginning,
                                primary = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tvInitialFocusTarget(focusState)
                                    .tvFocusDownTo(firstRelatedFocus)
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
                        .padding(horizontal = TV_DETAILS_RELATED_SIDE_MARGIN)
                        .onSizeChanged { relatedRowHeightPx = it.height.toFloat() }
                        .onFocusChanged {
                            isRelatedFocused = it.hasFocus
                            // Le cadre fixe n'appartient qu'à cette rangée : le
                            // laisser affiché après la sortie du focus le
                            // faisait flotter sur une fiche redescendue.
                            if (!it.hasFocus) focusSelector.clear()
                        }
                        .onFocusedBoundsChanged { focusedRelatedChild = it }
                ) {
                    RelatedTitlesRow(
                        title = stringResource(R.string.details_related_titles),
                        items = relatedStreams,
                        poster = { it.streamIcon },
                        label = { it.name },
                        onClick = onSelectRelated,
                        tvPivotEnabled = true,
                        firstItemFocusRequester = firstRelatedFocus,
                        state = relatedRowState
                    )
                }
            }
        }
        }

        // Cadre de focus fixe, au premier plan de la racine : il doit survoler
        // la colonne décalable sans être déplacé avec elle.
        TvFocusSelectorOverlay(focusSelector, modifier = Modifier.fillMaxSize())

        if (showFullPlot) {
            TvPlotDialog(
                title = details.name,
                plot = details.plot,
                onDismiss = { showFullPlot = false }
            )
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
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var isFocused by remember { mutableStateOf(false) }
    val description = stringResource(
        if (selected) R.string.media_rating_selected_description else R.string.media_rating_action_description,
        text
    )
    Box(
        modifier = modifier
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
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
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
                fontFamily = HankenGrotesk,
                // Le libellé n'a plus la largeur qu'il réclame mais un tiers de
                // la rangée : sans borne, « Ajouter aux favoris » passerait à la
                // ligne et déformerait la hauteur d'une seule des trois actions.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Bouton de lecture, au dessin de la fiche mobile : rayon 8 dp, texte blanc,
 * fond `AccentLavande` pour l'action principale et `Surface3` pour la
 * secondaire. Le focus TV s'ajoute par-dessus, en liseré `AccentLavandeHover`.
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
    icon: ImageVector,
    onClick: () -> Unit,
    primary: Boolean,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    val container = when {
        primary && isFocused -> AccentLavandeHover
        primary -> AccentLavande
        else -> Surface3
    }
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .onFocusChanged { isFocused = it.isFocused }
            .background(color = container, shape = shape)
            .border(
                width = 2.dp,
                color = if (isFocused) AccentLavandeHover else Color.Transparent,
                shape = shape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                fontFamily = HankenGrotesk
            )
        }
    }
}

/**
 * Ouvre le synopsis intégral ; n'apparaît que si le texte est tronqué.
 *
 * Volontairement dépouillé — ni cadre, ni fond, ni pastille : c'est un mot
 * sélectionnable dans le fil du texte, qui ne doit pas concurrencer le bouton
 * de lecture. Seule la couleur signale le focus.
 */
@Composable
private fun PlotMoreLink(onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Text(
        text = stringResource(R.string.details_plot_see_more),
        color = if (isFocused) AccentLavande else TextSecondary,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        fontFamily = HankenGrotesk,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(vertical = 2.dp)
    )
}

/**
 * Synopsis intégral dans une fenêtre par-dessus la fiche.
 *
 * Un dépliement en place aurait déplacé crédits, actions et boutons sous le
 * focus de l'utilisateur — le défaut que F28 évitait en refusant tout
 * « voir plus ». La fenêtre lève l'objection : rien ne bouge derrière, et la
 * touche Retour la referme.
 */
@Composable
private fun TvPlotDialog(
    title: String,
    plot: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val scrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Surface1.copy(alpha = 0.94f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .fillMaxHeight(0.8f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface3)
                    .padding(32.dp)
            ) {
                Text(
                    text = title.uppercase(),
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontFamily = BricolageGrotesque,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = plot,
                    color = TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    fontFamily = HankenGrotesk,
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        // Focalisable pour que le D-pad puisse faire défiler un
                        // synopsis plus long que la fenêtre.
                        .focusable()
                )
            }
        }
    }
}
