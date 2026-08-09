package com.cstv.app.presentation.series

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.onFocusedBoundsChanged
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import coil.compose.AsyncImage
import com.cstv.app.R
import com.cstv.app.domain.model.EpisodeLabel
import com.cstv.app.domain.model.MediaRatingValue
import com.cstv.app.domain.model.SeriesDetails
import com.cstv.app.domain.model.SeriesEpisode
import com.cstv.app.domain.model.SeriesStream
import com.cstv.app.domain.model.TrailerMedia
import com.cstv.app.presentation.components.LocalTvFocusSelector
import com.cstv.app.presentation.components.MediaDetailsTrailerBackdrop
import com.cstv.app.presentation.components.RelatedTitlesRow
import com.cstv.app.presentation.components.TrailerPreviewUiState
import com.cstv.app.presentation.components.TvFocusSelectorOverlay
import com.cstv.app.presentation.components.TvFocusSelectorState
import com.cstv.app.presentation.components.TvInitialFocusState
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
import kotlin.math.max

private val TV_SERIES_RELATED_PEEK = 96.dp
private val TV_SERIES_BOTTOM_RESERVE = 24.dp
private val TV_SERIES_DIVIDER = TextSecondary.copy(alpha = 0.35f)
private val TV_SERIES_SELECTOR_RADIUS = 12.dp
private const val TV_SERIES_HERO_ARTWORK_FRACTION = 0.48f

/** Position visuelle locale de la fiche TV, réinitialisée à chaque série. */
internal enum class TvSeriesDetailsSection { HERO, EPISODES, RELATED }

/** Directions that cross a visual-panel boundary and therefore require explicit focus handling. */
internal enum class TvSeriesDetailsTransition {
    HERO_TO_EPISODES,
    EPISODES_TO_HERO,
    EPISODES_TO_RELATED,
    RELATED_TO_EPISODES
}

/** Explicit destinations at the episodes-panel boundaries; ordinary episode-to-episode moves stay geometric. */
internal enum class TvSeriesEpisodesFocusTarget { SEASON_PILL, FIRST_EPISODE, LAST_EPISODE, EMPTY_SERIES }

internal data class TvSeriesPlaybackTarget(
    val episode: SeriesEpisode?,
    val isResume: Boolean
)

internal fun tvSeriesSeasonNumbers(details: SeriesDetails): List<Int> =
    (details.seasons.map { it.seasonNumber } + details.episodes.keys)
        .distinct()
        .sorted()

internal fun tvSeriesEpisodesForSeason(details: SeriesDetails, seasonNumber: Int): List<SeriesEpisode> =
    details.episodes[seasonNumber].orEmpty().sortedBy { it.episodeNum }

internal fun tvSeriesPlaybackTarget(details: SeriesDetails): TvSeriesPlaybackTarget {
    val episodes = details.episodes.values.flatten()
    val resume = episodes
        .filter { episode ->
            episode.resumePositionMs > 1_000L &&
                (episode.durationMs <= 0L || episode.resumePositionMs < episode.durationMs - 5_000L)
        }
        .maxByOrNull { it.lastAccessedAt }
    val firstEpisode = tvSeriesSeasonNumbers(details)
        .asSequence()
        .flatMap { tvSeriesEpisodesForSeason(details, it).asSequence() }
        .firstOrNull()
        ?: episodes.minWithOrNull(compareBy<SeriesEpisode> { it.seasonNum }.thenBy { it.episodeNum })
    return TvSeriesPlaybackTarget(episode = resume ?: firstEpisode, isResume = resume != null)
}

internal fun tvSeriesProgressFraction(episode: SeriesEpisode?): Float = when {
    episode == null || episode.resumePositionMs <= 0L || episode.durationMs <= 0L -> 0f
    else -> (episode.resumePositionMs.toFloat() / episode.durationMs.toFloat()).coerceIn(0f, 1f)
}

internal fun tvSeriesSectionAfter(
    section: TvSeriesDetailsSection,
    transition: TvSeriesDetailsTransition,
    hasRelatedTitles: Boolean
): TvSeriesDetailsSection = when (transition) {
    TvSeriesDetailsTransition.HERO_TO_EPISODES -> TvSeriesDetailsSection.EPISODES
    TvSeriesDetailsTransition.EPISODES_TO_HERO -> TvSeriesDetailsSection.HERO
    TvSeriesDetailsTransition.EPISODES_TO_RELATED -> if (hasRelatedTitles) {
        TvSeriesDetailsSection.RELATED
    } else {
        section
    }
    TvSeriesDetailsTransition.RELATED_TO_EPISODES -> if (section == TvSeriesDetailsSection.RELATED) {
        TvSeriesDetailsSection.EPISODES
    } else {
        section
    }
}

internal fun tvSeriesEpisodesEntryFocusTarget(
    hasSeasons: Boolean,
    hasEpisodes: Boolean,
    returnToLastEpisode: Boolean
): TvSeriesEpisodesFocusTarget = when {
    !hasSeasons -> TvSeriesEpisodesFocusTarget.EMPTY_SERIES
    returnToLastEpisode && hasEpisodes -> TvSeriesEpisodesFocusTarget.LAST_EPISODE
    else -> TvSeriesEpisodesFocusTarget.SEASON_PILL
}

internal fun tvSeriesSeasonDownFocusTarget(hasEpisodes: Boolean): TvSeriesEpisodesFocusTarget =
    if (hasEpisodes) TvSeriesEpisodesFocusTarget.FIRST_EPISODE else TvSeriesEpisodesFocusTarget.SEASON_PILL

/** Returns a displayable duration only when a real resume position and duration are available. */
internal fun tvSeriesRemainingDuration(episode: SeriesEpisode): String? {
    if (tvSeriesProgressFraction(episode) <= 0f) return null
    val remainingMinutes = ((episode.durationMs - episode.resumePositionMs).coerceAtLeast(0L) / 60_000L).toInt()
    if (remainingMinutes <= 0) return "0 min"
    val hours = remainingMinutes / 60
    val minutes = remainingMinutes % 60
    return if (hours > 0) "%dh%02d".format(hours, minutes) else "$minutes min"
}

internal fun tvSeriesRelatedShiftPx(
    episodePanelHeight: Float,
    relatedRowHeight: Float,
    bottomReserve: Float,
    screenHeight: Float
): Float = if (relatedRowHeight <= 0f) 0f else max(
    0f,
    episodePanelHeight + relatedRowHeight + bottomReserve - screenHeight
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SeriesDetailsTvLayout(
    details: SeriesDetails,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onEpisodeClick: (SeriesEpisode) -> Unit,
    onSearchQueryTriggered: (String) -> Unit,
    relatedSeries: List<SeriesStream>,
    onSelectRelated: (SeriesStream) -> Unit,
    mediaRating: MediaRatingValue?,
    isRatingSaving: Boolean,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    trailerState: TrailerPreviewUiState,
    onTrailerFailed: (TrailerMedia) -> Unit,
    trailerMuted: Boolean,
    modifier: Modifier = Modifier
) {
    val seasons = remember(details.seasons, details.episodes) { tvSeriesSeasonNumbers(details) }
    var selectedSeason by remember(details.seriesId, seasons) {
        mutableStateOf(seasons.firstOrNull())
    }
    val selectedEpisodes = remember(details, selectedSeason) {
        selectedSeason?.let { tvSeriesEpisodesForSeason(details, it) }.orEmpty()
    }
    val seasonNames = remember(details.seasons) {
        details.seasons.associate { it.seasonNumber to it.name }
    }
    val playbackTarget = remember(details) { tvSeriesPlaybackTarget(details) }
    val initialFocus = rememberTvInitialFocus(isTv = true, ready = true, targetKey = details.seriesId)
    var section by remember(details.seriesId) { mutableStateOf(TvSeriesDetailsSection.HERO) }
    var lastEpisodeFocused by remember(details.seriesId, selectedSeason) { mutableStateOf(false) }
    var returnToLastEpisode by remember(details.seriesId, selectedSeason) { mutableStateOf(false) }

    val seasonRequesters = remember(details.seriesId) { mutableStateMapOf<Int, FocusRequester>() }
    val selectedSeasonFocus = selectedSeason?.let { number ->
        seasonRequesters.getOrPut(number) { FocusRequester() }
    }
    val emptyEpisodesFocus = remember(details.seriesId) { FocusRequester() }
    val firstEpisodeFocus = remember(details.seriesId, selectedSeason) { FocusRequester() }
    val lastEpisodeFocus = remember(details.seriesId, selectedSeason) { FocusRequester() }
    val firstRelatedFocus = remember(details.seriesId) { FocusRequester() }
    val relatedRowState = remember(details.seriesId) { LazyListState() }

    LaunchedEffect(section) {
        when (section) {
            TvSeriesDetailsSection.HERO -> Unit
            TvSeriesDetailsSection.EPISODES -> {
                val focusTarget = tvSeriesEpisodesEntryFocusTarget(
                    hasSeasons = seasons.isNotEmpty(),
                    hasEpisodes = selectedEpisodes.isNotEmpty(),
                    returnToLastEpisode = returnToLastEpisode
                )
                returnToLastEpisode = false
                val requester = when (focusTarget) {
                    TvSeriesEpisodesFocusTarget.SEASON_PILL -> selectedSeasonFocus ?: emptyEpisodesFocus
                    TvSeriesEpisodesFocusTarget.LAST_EPISODE -> lastEpisodeFocus
                    TvSeriesEpisodesFocusTarget.EMPTY_SERIES -> emptyEpisodesFocus
                    TvSeriesEpisodesFocusTarget.FIRST_EPISODE -> firstEpisodeFocus
                }
                runCatching { requester.requestFocus() }
            }
            TvSeriesDetailsSection.RELATED -> {
                if (relatedSeries.isNotEmpty()) runCatching { firstRelatedFocus.requestFocus() }
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(Surface1)) {
        val screenHeightDp = maxHeight
        val density = LocalDensity.current
        val screenHeightPx = with(density) { screenHeightDp.toPx() }
        val bottomReservePx = with(density) { TV_SERIES_BOTTOM_RESERVE.toPx() }
        val trailerMedia = remember(details.seriesId) { TrailerMedia.Series(details.seriesId) }
        val trailerPlaying = (trailerState as? TrailerPreviewUiState.Playing)?.preview?.media == trailerMedia

        TvSeriesHeroArtwork(
            details = details,
            trailerMedia = trailerMedia,
            trailerPlaying = trailerPlaying,
            trailerState = trailerState,
            onTrailerFailed = onTrailerFailed,
            trailerMuted = trailerMuted
        )

        var episodePanelHeightPx by remember { mutableFloatStateOf(0f) }
        var relatedRowHeightPx by remember { mutableFloatStateOf(0f) }
        val relatedShift = tvSeriesRelatedShiftPx(
            episodePanelHeight = episodePanelHeightPx,
            relatedRowHeight = relatedRowHeightPx,
            bottomReserve = bottomReservePx,
            screenHeight = screenHeightPx
        )
        val targetTranslation = when (section) {
            TvSeriesDetailsSection.HERO -> 0f
            TvSeriesDetailsSection.EPISODES -> -screenHeightPx
            TvSeriesDetailsSection.RELATED -> -(screenHeightPx + relatedShift)
        }
        val translation by animateFloatAsState(
            targetValue = targetTranslation,
            animationSpec = tween(durationMillis = 300),
            label = "seriesDetailsSection"
        )

        val focusSelector = remember { TvFocusSelectorState() }
        var focusedRelatedChild by remember { mutableStateOf<LayoutCoordinates?>(null) }
        LaunchedEffect(focusSelector) {
            snapshotFlow { translation }.collect {
                val coordinates = focusedRelatedChild?.takeIf { it.isAttached } ?: return@collect
                focusSelector.publishStabilised(
                    bounds = Rect(coordinates.positionInRoot(), coordinates.size.toSize()),
                    cornerRadius = TV_SERIES_SELECTOR_RADIUS
                )
            }
        }

        CompositionLocalProvider(LocalTvFocusSelector provides focusSelector) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
                    .graphicsLayer { translationY = translation }
            ) {
                TvSeriesHeroPanel(
                    details = details,
                    isFavorite = isFavorite,
                    playbackTarget = playbackTarget,
                    mediaRating = mediaRating,
                    isRatingSaving = isRatingSaving,
                    onToggleFavorite = onToggleFavorite,
                    onLike = onLike,
                    onDislike = onDislike,
                    onSearchQueryTriggered = onSearchQueryTriggered,
                    onPlay = { playbackTarget.episode?.let(onEpisodeClick) },
                    onMoveToEpisodes = {
                        section = tvSeriesSectionAfter(
                            section,
                            TvSeriesDetailsTransition.HERO_TO_EPISODES,
                            relatedSeries.isNotEmpty()
                        )
                    },
                    active = section == TvSeriesDetailsSection.HERO,
                    initialFocus = initialFocus,
                    modifier = Modifier
                        .height(screenHeightDp)
                )

                TvSeriesEpisodesPanel(
                    seasonNames = seasonNames,
                    seasons = seasons,
                    selectedSeason = selectedSeason,
                    selectedEpisodes = selectedEpisodes,
                    active = section == TvSeriesDetailsSection.EPISODES,
                    firstEpisodeFocus = firstEpisodeFocus,
                    lastEpisodeFocus = lastEpisodeFocus,
                    emptyEpisodesFocus = emptyEpisodesFocus,
                    seasonFocus = { number -> seasonRequesters.getOrPut(number) { FocusRequester() } },
                    onSeasonFocused = { number ->
                        if (number != selectedSeason) {
                            selectedSeason = number
                            lastEpisodeFocused = false
                        }
                    },
                    onEpisodeClick = onEpisodeClick,
                    onBackToHero = {
                        section = tvSeriesSectionAfter(
                            section,
                            TvSeriesDetailsTransition.EPISODES_TO_HERO,
                            relatedSeries.isNotEmpty()
                        )
                    },
                    onLastEpisodeFocused = { lastEpisodeFocused = it },
                    onOpenRelated = {
                        val nextSection = tvSeriesSectionAfter(
                            section,
                            TvSeriesDetailsTransition.EPISODES_TO_RELATED,
                            relatedSeries.isNotEmpty()
                        )
                        val didOpen = nextSection == TvSeriesDetailsSection.RELATED
                        section = nextSection
                        didOpen
                    },
                    modifier = Modifier
                        .height(screenHeightDp - TV_SERIES_RELATED_PEEK)
                        .onSizeChanged { episodePanelHeightPx = it.height.toFloat() }
                        .background(Surface1)
                )

                if (relatedSeries.isNotEmpty()) {
                    val relatedVisible = lastEpisodeFocused || section == TvSeriesDetailsSection.RELATED
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 48.dp, end = 48.dp, bottom = TV_SERIES_BOTTOM_RESERVE)
                            .onSizeChanged { relatedRowHeightPx = it.height.toFloat() }
                            .onFocusedBoundsChanged { focusedRelatedChild = it }
                            .onFocusChanged {
                                if (!it.hasFocus && section != TvSeriesDetailsSection.RELATED) focusSelector.clear()
                            }
                            .focusProperties { canFocus = section == TvSeriesDetailsSection.RELATED }
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp && section == TvSeriesDetailsSection.RELATED) {
                                    returnToLastEpisode = true
                                    section = tvSeriesSectionAfter(
                                        section,
                                        TvSeriesDetailsTransition.RELATED_TO_EPISODES,
                                        relatedSeries.isNotEmpty()
                                    )
                                    true
                                } else false
                            }
                            .graphicsLayer { alpha = if (relatedVisible) 1f else 0f }
                    ) {
                        RelatedTitlesRow(
                            title = stringResource(R.string.details_related_titles),
                            items = relatedSeries,
                            poster = { it.cover },
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
        TvFocusSelectorOverlay(focusSelector, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun TvSeriesHeroArtwork(
    details: SeriesDetails,
    trailerMedia: TrailerMedia,
    trailerPlaying: Boolean,
    trailerState: TrailerPreviewUiState,
    onTrailerFailed: (TrailerMedia) -> Unit,
    trailerMuted: Boolean
) {
    Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(TV_SERIES_HERO_ARTWORK_FRACTION)) {
        when {
            trailerPlaying -> MediaDetailsTrailerBackdrop(
                media = trailerMedia,
                state = trailerState,
                posterUrl = details.cover,
                onPlaybackFailed = onTrailerFailed,
                muted = trailerMuted,
                modifier = Modifier.fillMaxSize()
            )
            !details.cover.isNullOrBlank() -> AsyncImage(
                model = details.cover,
                contentDescription = details.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            else -> Box(
                modifier = Modifier.fillMaxSize().background(Surface3),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Warning, null, tint = TextSecondary, modifier = Modifier.size(54.dp))
            }
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0.25f to Color.Transparent, 1f to Surface1)
            )
        )
    }
}

@Composable
private fun TvSeriesHeroPanel(
    details: SeriesDetails,
    isFavorite: Boolean,
    playbackTarget: TvSeriesPlaybackTarget,
    mediaRating: MediaRatingValue?,
    isRatingSaving: Boolean,
    onToggleFavorite: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onSearchQueryTriggered: (String) -> Unit,
    onPlay: () -> Unit,
    onMoveToEpisodes: () -> Unit,
    active: Boolean,
    initialFocus: TvInitialFocusState,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.weight(TV_SERIES_HERO_ARTWORK_FRACTION).fillMaxHeight())
        Column(
            modifier = Modifier
                .weight(1f - TV_SERIES_HERO_ARTWORK_FRACTION)
                .fillMaxHeight()
                .padding(end = 48.dp, top = 30.dp, bottom = 24.dp)
        ) {
            Text(
                text = details.name.uppercase(),
                color = TextPrimary,
                fontSize = 30.sp,
                fontFamily = BricolageGrotesque,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            TvSeriesMetadata(details)
            Spacer(Modifier.height(12.dp))
            details.plot?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    color = TextSecondary,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp,
                    fontFamily = HankenGrotesk,
                    maxLines = 7,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            } ?: Spacer(Modifier.weight(1f, fill = false))
            HorizontalDivider(color = TV_SERIES_DIVIDER, thickness = 0.5.dp)
            TvSeriesCredits(stringResource(R.string.details_credits_director), details.director, active, onSearchQueryTriggered)
            TvSeriesCredits(stringResource(R.string.details_credits_cast), details.actors, active, onSearchQueryTriggered)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                TvSeriesAction(Icons.Default.Star, stringResource(if (isFavorite) R.string.vod_details_remove_favorite else R.string.vod_details_add_favorite), if (isFavorite) FavoriteGold else TextPrimary, isFavorite, active, onToggleFavorite)
                TvSeriesActionDivider()
                TvSeriesAction(Icons.Default.ThumbUp, stringResource(R.string.media_rating_like), if (mediaRating == MediaRatingValue.LIKE) RatingLike else TextPrimary, mediaRating == MediaRatingValue.LIKE, active && !isRatingSaving, onLike)
                TvSeriesActionDivider()
                TvSeriesAction(Icons.Default.ThumbDown, stringResource(R.string.media_rating_dislike), if (mediaRating == MediaRatingValue.DISLIKE) RatingDislike else TextPrimary, mediaRating == MediaRatingValue.DISLIKE, active && !isRatingSaving, onDislike)
            }
            Spacer(Modifier.height(18.dp))
            val episode = playbackTarget.episode
            val label = if (playbackTarget.isResume && episode != null) {
                stringResource(R.string.series_details_resume_episode, EpisodeLabel.format(episode.seasonNum, episode.episodeNum).orEmpty())
            } else stringResource(R.string.series_details_play)
            TvSeriesPlayButton(
                text = label,
                onClick = onPlay,
                active = active,
                onMoveDown = onMoveToEpisodes,
                modifier = Modifier.tvInitialFocusTarget(initialFocus, active)
            )
            if (playbackTarget.isResume && tvSeriesProgressFraction(episode) > 0f) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { tvSeriesProgressFraction(episode) },
                    color = AccentLavande,
                    trackColor = Surface3,
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

@Composable
private fun TvSeriesMetadata(details: SeriesDetails) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        formatReleaseYear(details.releaseDate).takeIf { it.isNotBlank() }?.let {
            Text(it, color = AccentLavande, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = HankenGrotesk)
        }
        details.genre?.takeIf { it.isNotBlank() }?.let {
            TvSeriesMetadataSeparator()
            Text(it, color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = HankenGrotesk)
        }
        details.rating?.takeIf { it.isNotBlank() && it != "0" && it != "0.0" }?.let {
            TvSeriesMetadataSeparator()
            Icon(Icons.Default.Star, null, tint = FavoriteGold, modifier = Modifier.size(15.dp))
            Text(it, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = HankenGrotesk)
        }
    }
}

@Composable
private fun TvSeriesMetadataSeparator() = Text("|", color = TV_SERIES_DIVIDER, fontSize = 14.sp)

@Composable
private fun TvSeriesCredits(label: String, names: String?, active: Boolean, onClick: (String) -> Unit) {
    val entries = remember(names) { names.orEmpty().split(',').map(String::trim).filter(String::isNotBlank) }
    if (entries.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 7.dp)) {
        Text("$label :", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            items(entries) { name ->
                var focused by remember { mutableStateOf(false) }
                Text(
                    text = name,
                    color = if (focused) AccentLavande else TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .focusProperties { canFocus = active }
                        .onFocusChanged { focused = it.isFocused }
                        .background(if (focused) AccentLavande.copy(alpha = 0.15f) else Surface3)
                        .clickable { onClick(name) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun TvSeriesAction(
    icon: ImageVector,
    text: String,
    tint: Color,
    selected: Boolean,
    active: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .focusProperties { canFocus = active }
            .onFocusChanged { focused = it.isFocused }
            .background(if (focused) AccentLavande.copy(alpha = 0.15f) else Color.Transparent)
            .border(1.5.dp, if (focused) AccentLavande else Color.Transparent, RoundedCornerShape(20.dp))
            .clickable(enabled = active) { onClick() }
            .padding(horizontal = 11.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text(text, color = if (selected) tint else TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TvSeriesActionDivider() = Box(Modifier.width(1.dp).height(16.dp).background(TV_SERIES_DIVIDER))

@Composable
private fun TvSeriesPlayButton(
    text: String,
    onClick: () -> Unit,
    active: Boolean,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .focusProperties { canFocus = active }
            .onFocusChanged { focused = it.isFocused }
            .background(if (focused) AccentLavandeHover else AccentLavande)
            .border(2.dp, if (focused) AccentLavandeHover else Color.Transparent, shape)
            .clickable(enabled = active) { onClick() }
            .onKeyEvent { event ->
                if (active && event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    onMoveDown()
                    true
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = TextPrimary, fontFamily = HankenGrotesk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun TvSeriesEpisodesPanel(
    seasonNames: Map<Int, String>,
    seasons: List<Int>,
    selectedSeason: Int?,
    selectedEpisodes: List<SeriesEpisode>,
    active: Boolean,
    firstEpisodeFocus: FocusRequester,
    lastEpisodeFocus: FocusRequester,
    emptyEpisodesFocus: FocusRequester,
    seasonFocus: (Int) -> FocusRequester,
    onSeasonFocused: (Int) -> Unit,
    onEpisodeClick: (SeriesEpisode) -> Unit,
    onBackToHero: () -> Unit,
    onLastEpisodeFocused: (Boolean) -> Unit,
    onOpenRelated: () -> Boolean,
    modifier: Modifier = Modifier
) {
    val episodeListState = rememberLazyListState()
    LaunchedEffect(selectedSeason) { episodeListState.scrollToItem(0) }
    Column(modifier = modifier.padding(horizontal = 48.dp, vertical = 26.dp)) {
        Text(stringResource(R.string.series_details_seasons_label), color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        if (seasons.isEmpty()) {
            var focused by remember { mutableStateOf(false) }
            Text(
                text = stringResource(R.string.series_details_no_episodes),
                color = if (focused) TextPrimary else TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (focused) Surface3 else Color.Transparent)
                    .border(1.dp, if (focused) AccentLavande else Color.Transparent, RoundedCornerShape(12.dp))
                    .focusRequester(emptyEpisodesFocus)
                    .focusProperties { canFocus = active }
                    .onFocusChanged { focused = it.isFocused }
                    .focusable()
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) { onBackToHero(); true } else false
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                items(seasons, key = { it }) { seasonNumber ->
                    val selected = seasonNumber == selectedSeason
                    var focused by remember { mutableStateOf(false) }
                    val seasonName = seasonNames[seasonNumber]
                        ?: stringResource(R.string.series_details_season_default, seasonNumber)
                    Text(
                        text = seasonName,
                        color = if (selected || focused) TextPrimary else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .focusRequester(seasonFocus(seasonNumber))
                            .focusProperties { canFocus = active }
                            .clip(RoundedCornerShape(18.dp))
                            .onFocusChanged {
                                focused = it.isFocused
                                if (it.isFocused) onSeasonFocused(seasonNumber)
                            }
                            .background(if (selected || focused) AccentLavande else Surface3)
                            .clickable(enabled = active) { onSeasonFocused(seasonNumber) }
                            .onKeyEvent { event ->
                                when {
                                    event.type != KeyEventType.KeyDown -> false
                                    event.key == Key.DirectionUp -> { onBackToHero(); true }
                                    event.key == Key.DirectionDown && tvSeriesSeasonDownFocusTarget(selectedEpisodes.isNotEmpty()) == TvSeriesEpisodesFocusTarget.FIRST_EPISODE -> runCatching { firstEpisodeFocus.requestFocus() }.isSuccess
                                    else -> false
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.series_details_episodes_label), color = AccentLavande, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        if (selectedEpisodes.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.series_details_no_episodes_for_season), color = TextSecondary)
            }
        } else {
            LazyColumn(
                state = episodeListState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                itemsIndexed(selectedEpisodes, key = { _, episode -> episode.id }) { index, episode ->
                    TvSeriesEpisodeCard(
                        episode = episode,
                        active = active,
                        modifier = Modifier
                            .then(if (index == 0) Modifier.focusRequester(firstEpisodeFocus) else Modifier)
                            .then(if (index == selectedEpisodes.lastIndex) Modifier.focusRequester(lastEpisodeFocus) else Modifier)
                            .onFocusChanged { if (index == selectedEpisodes.lastIndex) onLastEpisodeFocused(it.isFocused) }
                            .onKeyEvent { event ->
                                when {
                                    event.type != KeyEventType.KeyDown -> false
                                    event.key == Key.DirectionUp && index == 0 -> runCatching { seasonFocus(selectedSeason ?: seasons.first()).requestFocus() }.isSuccess
                                    event.key == Key.DirectionDown && index == selectedEpisodes.lastIndex -> onOpenRelated()
                                    else -> false
                                }
                            },
                        onClick = { onEpisodeClick(episode) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TvSeriesEpisodeCard(episode: SeriesEpisode, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val progress = tvSeriesProgressFraction(episode)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .focusProperties { canFocus = active }
            .onFocusChanged { focused = it.isFocused }
            .border(1.dp, if (focused) AccentLavande else TV_SERIES_DIVIDER, RoundedCornerShape(12.dp))
            .background(if (focused) Surface3.copy(alpha = 0.9f) else Surface3.copy(alpha = 0.7f))
            .clickable(enabled = active) { onClick() }
            .padding(10.dp)
    ) {
        Box(Modifier.size(width = 176.dp, height = 99.dp).clip(RoundedCornerShape(8.dp)).background(Surface1), contentAlignment = Alignment.Center) {
            if (!episode.movieImage.isNullOrBlank()) AsyncImage(episode.movieImage, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Default.PlayArrow, null, tint = TextSecondary, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("${episode.episodeNum.toString().padStart(2, '0')}  ${episode.title}", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            episode.plot.takeIf { it.isNotBlank() }?.let { Text(it, color = TextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp)) }
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(150.dp)) {
            val remainingDuration = tvSeriesRemainingDuration(episode)
            Text(
                text = remainingDuration?.let { stringResource(R.string.series_details_resume_info, it) }
                    ?: episode.duration.ifBlank { stringResource(R.string.series_details_not_watched) },
                color = TextSecondary,
                fontSize = 12.sp
            )
            if (progress > 0f) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    color = AccentLavande,
                    trackColor = Surface1,
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp))
                )
            }
        }
    }
}
