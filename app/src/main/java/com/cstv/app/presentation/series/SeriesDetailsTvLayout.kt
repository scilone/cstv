package com.cstv.app.presentation.series
import com.cstv.app.presentation.common.displayLabel

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.onFocusedBoundsChanged
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.runtime.withFrameNanos
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

/**
 * Amorce des « titres associés » laissée visible sous le panneau des épisodes.
 *
 * Volontairement réduite : cette réserve est vide tant que le focus n'a pas
 * atteint le dernier épisode, et une amorce d'une hauteur de vignette creusait
 * un vide franc au bas de l'écran dès les premiers épisodes. Le peu qui reste
 * suffit à signaler qu'il y a quelque chose en dessous, et la hauteur récupérée
 * va aux épisodes — voir [TV_SERIES_EPISODE_THUMB_HEIGHT].
 */
private val TV_SERIES_RELATED_PEEK = 40.dp
private val TV_SERIES_BOTTOM_RESERVE = 24.dp
private val TV_SERIES_DIVIDER = TextSecondary.copy(alpha = 0.35f)
private val TV_SERIES_SELECTOR_RADIUS = 12.dp
private const val TV_SERIES_HERO_ARTWORK_FRACTION = 0.48f

/**
 * Vignette d'épisode dimensionnée pour que **quatre** cartes tiennent dans le
 * panneau sans défiler sur un écran TV 1080p (540 dp de haut) : la carte fait
 * la hauteur de la vignette plus son rembourrage, soit ~84 dp, et quatre cartes
 * espacées de [TV_SERIES_EPISODE_SPACING] rentrent sous l'en-tête saisons.
 */
private val TV_SERIES_EPISODE_THUMB_WIDTH = 120.dp
private val TV_SERIES_EPISODE_THUMB_HEIGHT = 68.dp
private val TV_SERIES_EPISODE_SPACING = 8.dp

/**
 * Le focus ne peut être posé sur la première vignette des titres associés
 * qu'une fois celle-ci composée. À la réouverture de la rangée, elle vient
 * d'être ramenée dans le champ par un `scrollToItem` : quelques frames de
 * rattrapage évitent une demande de focus perdue, qui laisserait la rangée
 * ouverte mais inatteignable au D-pad.
 */
private const val TV_SERIES_RELATED_FOCUS_ATTEMPTS = 6

/**
 * Chevron d'appel vers le panneau des épisodes.
 *
 * Le panneau héros occupe toute la hauteur de l'écran et ne laisse rien
 * dépasser : rien n'indiquait qu'une descente du D-pad ouvrait la liste des
 * épisodes. Un va-et-vient vertical de quelques pixels suffit à le signaler
 * sans concurrencer le bouton de lecture — l'amplitude reste sous la hauteur du
 * chevron pour que le mouvement se lise comme une respiration, pas comme un
 * élément qui défile.
 */
private val TV_SERIES_SCROLL_HINT_SIZE = 28.dp
private val TV_SERIES_SCROLL_HINT_TRAVEL = 6.dp
private const val TV_SERIES_SCROLL_HINT_PERIOD_MS = 1400

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
internal enum class TvSeriesEpisodesFocusTarget { SEASON_PILL, FIRST_EPISODE, LAST_EPISODE, RESUME_EPISODE, EMPTY_SERIES }

/**
 * Emplacements de la liste d'épisodes qui portent un `FocusRequester` nommé.
 *
 * Un même item peut prétendre à plusieurs rôles — l'épisode repris est parfois
 * le premier ou le dernier de la saison. Deux `Modifier.focusRequester` sur le
 * même nœud ne se cumulent pas : cette précédence unique, partagée par la pose
 * du modificateur et par la résolution de la cible d'entrée, garantit que les
 * deux désignent toujours le même requester.
 *
 * Fonction pure, sans dépendance Compose, pour rester testable en JVM.
 */
internal enum class TvSeriesEpisodeSlot { FIRST, LAST, RESUME, NONE }

internal fun tvSeriesEpisodeSlot(index: Int, lastIndex: Int, resumeIndex: Int?): TvSeriesEpisodeSlot = when {
    index == 0 -> TvSeriesEpisodeSlot.FIRST
    index == lastIndex -> TvSeriesEpisodeSlot.LAST
    resumeIndex != null && index == resumeIndex -> TvSeriesEpisodeSlot.RESUME
    else -> TvSeriesEpisodeSlot.NONE
}

/**
 * Rang, dans la saison affichée, de l'épisode consulté le plus récemment —
 * `null` si aucun ne l'a été.
 *
 * Prolonge [tvSeriesInitialSeason] au cran suivant : atterrir en tête d'une
 * saison de trente épisodes obligeait à descendre jusqu'à celui qu'on suit.
 * Même repère que pour la saison, `lastAccessedAt`, pour que les deux
 * présélections désignent le même épisode.
 *
 * Fonction pure, sans dépendance Compose, pour rester testable en JVM.
 */
internal fun tvSeriesInitialEpisodeIndex(episodes: List<SeriesEpisode>): Int? = episodes
    .withIndex()
    .filter { it.value.lastAccessedAt > 0L }
    .maxByOrNull { it.value.lastAccessedAt }
    ?.index

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

/**
 * Saison présélectionnée à l'ouverture de la fiche : celle de l'épisode
 * consulté le plus récemment, la saison 1 à défaut.
 *
 * Retenir systématiquement la première saison renvoyait au début d'une série
 * dont l'utilisateur suit la dernière saison — le panneau s'ouvrait sur des
 * épisodes déjà vus et il fallait retraverser toutes les saisons.
 *
 * Le repère est `lastAccessedAt`, pas la position de reprise : un épisode
 * terminé la remet à zéro (voir [SeriesEpisode.watched]) mais reste bien le
 * dernier consulté — c'est même le cas le plus courant, celui de la série
 * suivie épisode par épisode.
 *
 * Une saison inconnue de [tvSeriesSeasonNumbers] (position héritée d'un
 * catalogue depuis remanié) est ignorée plutôt que sélectionnée à vide.
 *
 * Fonction pure, sans dépendance Compose, pour rester testable en JVM.
 */
internal fun tvSeriesInitialSeason(details: SeriesDetails): Int? {
    val seasons = tvSeriesSeasonNumbers(details)
    val lastAccessed = details.episodes.values.asSequence()
        .flatten()
        .filter { it.lastAccessedAt > 0L }
        .maxByOrNull { it.lastAccessedAt }
    return lastAccessed?.seasonNum?.takeIf { it in seasons } ?: seasons.firstOrNull()
}

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
    returnToLastEpisode: Boolean,
    resumeEpisodeIndex: Int? = null
): TvSeriesEpisodesFocusTarget = when {
    !hasSeasons -> TvSeriesEpisodesFocusTarget.EMPTY_SERIES
    returnToLastEpisode && hasEpisodes -> TvSeriesEpisodesFocusTarget.LAST_EPISODE
    hasEpisodes && resumeEpisodeIndex != null -> TvSeriesEpisodesFocusTarget.RESUME_EPISODE
    else -> TvSeriesEpisodesFocusTarget.SEASON_PILL
}

internal fun tvSeriesSeasonDownFocusTarget(hasEpisodes: Boolean): TvSeriesEpisodesFocusTarget =
    if (hasEpisodes) TvSeriesEpisodesFocusTarget.FIRST_EPISODE else TvSeriesEpisodesFocusTarget.SEASON_PILL

/**
 * Avancement affiché sur la carte d'un épisode : plein pour un épisode déjà vu
 * en entier, sinon la fraction reprise. Un épisode terminé n'a plus de position
 * de reprise ([SeriesEpisode.watched] en tient lieu), sans quoi il n'affichait
 * aucune chronologie et se confondait avec un épisode jamais lancé.
 */
internal fun tvSeriesEpisodeProgress(episode: SeriesEpisode): Float =
    if (episode.watched) 1f else tvSeriesProgressFraction(episode)

/** Returns a displayable duration only when a real resume position and duration are available. */
internal fun tvSeriesRemainingDuration(episode: SeriesEpisode): String? {
    if (episode.watched || tvSeriesProgressFraction(episode) <= 0f) return null
    val remainingMinutes = ((episode.durationMs - episode.resumePositionMs).coerceAtLeast(0L) / 60_000L).toInt()
    if (remainingMinutes <= 0) return "0 min"
    val hours = remainingMinutes / 60
    val minutes = remainingMinutes % 60
    return if (hours > 0) "%dh%02d".format(hours, minutes) else "$minutes min"
}

/** Information de droite d'une carte d'épisode : une seule ligne, un seul sens. */
internal enum class TvSeriesEpisodeStatus { WATCHED, REMAINING, DURATION, NONE }

/**
 * Ce que la carte d'un épisode affiche à droite.
 *
 * L'ancienne règle mélangeait deux axes — « durée du média » et « état de
 * visionnage » — sur la même ligne : un épisode dont le panel ne renseigne pas
 * la durée affichait « Non vu » **parce que** la durée manquait, et non parce
 * qu'il n'avait pas été vu. Deux épisodes au même état de visionnage
 * s'affichaient donc différemment, ce qui n'avait effectivement aucun sens.
 *
 * La ligne ne parle plus que de temps : temps restant si une reprise est en
 * cours, « Vu » si l'épisode est terminé, durée totale sinon, et rien du tout
 * quand le panel ne la renseigne pas — `"00:00"` étant la valeur de repli des
 * DTO Xtream, elle vaut absence de donnée.
 *
 * Fonction pure, sans dépendance Compose, pour rester testable en JVM.
 */
internal fun tvSeriesEpisodeStatus(episode: SeriesEpisode): TvSeriesEpisodeStatus = when {
    episode.watched -> TvSeriesEpisodeStatus.WATCHED
    tvSeriesRemainingDuration(episode) != null -> TvSeriesEpisodeStatus.REMAINING
    episode.duration.isNotBlank() && episode.duration != "00:00" -> TvSeriesEpisodeStatus.DURATION
    else -> TvSeriesEpisodeStatus.NONE
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
    /** F39 §8.6 (évolution PO) : voir SeriesDetailsScreen. */
    hasMultipleVersions: Boolean = false,
    onOpenVersions: () -> Unit = {},
    ageRating: Int? = null,
    modifier: Modifier = Modifier
) {
    val seasons = remember(details.seasons, details.episodes) { tvSeriesSeasonNumbers(details) }
    var selectedSeason by remember(details.seriesId, seasons) {
        mutableStateOf(tvSeriesInitialSeason(details))
    }
    val selectedEpisodes = remember(details, selectedSeason) {
        selectedSeason?.let { tvSeriesEpisodesForSeason(details, it) }.orEmpty()
    }
    val seasonNames = remember(details.seasons) {
        details.seasons.associate { it.seasonNumber to it.name }
    }
    val playbackTarget = remember(details) { tvSeriesPlaybackTarget(details) }
    val initialFocus = rememberTvInitialFocus(
        isTv = true,
        ready = true,
        targetKey = Triple(details.seriesId, playbackTarget.isResume, hasMultipleVersions)
    )
    LaunchedEffect(details.seriesId, playbackTarget.isResume, hasMultipleVersions) {
        repeat(10) {
            withFrameNanos { }
            runCatching { initialFocus.requester.requestFocus() }
        }
    }
    var section by remember(details.seriesId) { mutableStateOf(TvSeriesDetailsSection.HERO) }
    var returnToLastEpisode by remember(details.seriesId, selectedSeason) { mutableStateOf(false) }

    val seasonRequesters = remember(details.seriesId) { mutableStateMapOf<Int, FocusRequester>() }
    val selectedSeasonFocus = selectedSeason?.let { number ->
        seasonRequesters.getOrPut(number) { FocusRequester() }
    }
    val emptyEpisodesFocus = remember(details.seriesId) { FocusRequester() }
    val firstEpisodeFocus = remember(details.seriesId, selectedSeason) { FocusRequester() }
    val lastEpisodeFocus = remember(details.seriesId, selectedSeason) { FocusRequester() }
    val resumeEpisodeFocus = remember(details.seriesId, selectedSeason) { FocusRequester() }
    val resumeEpisodeIndex = remember(selectedEpisodes) { tvSeriesInitialEpisodeIndex(selectedEpisodes) }
    val episodeListState = remember(details.seriesId) { LazyListState() }
    val firstRelatedFocus = remember(details.seriesId) { FocusRequester() }
    val seasonRowState = remember(details.seriesId) { LazyListState() }
    val relatedRowState = remember(details.seriesId) { LazyListState() }
    var relatedFocused by remember(details.seriesId) { mutableStateOf(false) }

    LaunchedEffect(section) {
        when (section) {
            TvSeriesDetailsSection.HERO -> Unit
            TvSeriesDetailsSection.EPISODES -> {
                val focusTarget = tvSeriesEpisodesEntryFocusTarget(
                    hasSeasons = seasons.isNotEmpty(),
                    hasEpisodes = selectedEpisodes.isNotEmpty(),
                    returnToLastEpisode = returnToLastEpisode,
                    resumeEpisodeIndex = resumeEpisodeIndex
                )
                returnToLastEpisode = false
                val requester = when (focusTarget) {
                    TvSeriesEpisodesFocusTarget.SEASON_PILL -> selectedSeasonFocus ?: emptyEpisodesFocus
                    TvSeriesEpisodesFocusTarget.LAST_EPISODE -> lastEpisodeFocus
                    TvSeriesEpisodesFocusTarget.EMPTY_SERIES -> emptyEpisodesFocus
                    TvSeriesEpisodesFocusTarget.FIRST_EPISODE -> firstEpisodeFocus
                    TvSeriesEpisodesFocusTarget.RESUME_EPISODE -> {
                        val index = resumeEpisodeIndex ?: 0
                        // La liste peut compter trente épisodes : celui qu'on
                        // reprend n'est composé qu'une fois ramené dans le
                        // champ, sans quoi son requester n'a aucun nœud.
                        episodeListState.scrollToItem(index)
                        when (tvSeriesEpisodeSlot(index, selectedEpisodes.lastIndex, resumeEpisodeIndex)) {
                            TvSeriesEpisodeSlot.FIRST -> firstEpisodeFocus
                            TvSeriesEpisodeSlot.LAST -> lastEpisodeFocus
                            else -> resumeEpisodeFocus
                        }
                    }
                }
                // La saison présélectionnée n'est plus forcément la première
                // (voir tvSeriesInitialSeason) : sur une série à nombreuses
                // saisons, sa pastille peut être hors de la fenêtre de
                // composition de la `LazyRow`, et son `FocusRequester` sans
                // nœud auquel s'adresser. La ramener dans le champ la compose.
                if (focusTarget == TvSeriesEpisodesFocusTarget.SEASON_PILL) {
                    val index = seasons.indexOf(selectedSeason)
                    if (index > 0) seasonRowState.scrollToItem(index)
                }
                runCatching { requester.requestFocus() }
            }
            TvSeriesDetailsSection.RELATED -> {
                if (relatedSeries.isNotEmpty()) {
                    // Réouverture : la rangée est restée là où le pivot l'avait
                    // laissée à la fermeture. La première vignette peut alors
                    // être sortie de la fenêtre de composition de la `LazyRow`,
                    // et son `FocusRequester` n'a plus de nœud auquel s'adresser
                    // — la demande de focus échouait silencieusement, la rangée
                    // s'ouvrait sans cadre et sans réponse au D-pad. La ramener
                    // en tête la recompose, et les quelques passes suivantes
                    // couvrent le délai avant qu'elle soit posée.
                    relatedRowState.scrollToItem(0)
                    repeat(TV_SERIES_RELATED_FOCUS_ATTEMPTS) {
                        if (relatedFocused) return@LaunchedEffect
                        runCatching { firstRelatedFocus.requestFocus() }
                        withFrameNanos { }
                    }
                }
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
            // Hors du hero, le trailer est masqué par le voile ci-dessous : le
            // laisser sonner sur les saisons/épisodes n'a plus de sens. Le
            // choix de l'utilisateur ([trailerMuted]) est seulement forcé, pas
            // écrasé — la remontée sur le hero le restitue tel quel.
            trailerMuted = trailerMuted || section != TvSeriesDetailsSection.HERO
        )

        // Le visuel (ou le trailer) du hero occupe toute la hauteur de l'écran,
        // derrière les panneaux qui défilent par-dessus. Le panneau des épisodes
        // ne le couvre que sur sa propre hauteur : l'amorce des titres associés
        // laissait donc dépasser un bandeau de trailer au bas de l'écran. Ce
        // voile masque l'arrière-plan dès qu'on quitte le hero, pour que les
        // sections saisons/épisodes et titres associés soient sur fond plein.
        val backdropScrim by animateFloatAsState(
            targetValue = if (section == TvSeriesDetailsSection.HERO) 0f else 1f,
            animationSpec = tween(durationMillis = 300),
            label = "seriesDetailsBackdropScrim"
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = backdropScrim }
                .background(Surface1)
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
                    ageRating = ageRating,
                    isRatingSaving = isRatingSaving,
                    onToggleFavorite = onToggleFavorite,
                    onLike = onLike,
                    onDislike = onDislike,
                    onSearchQueryTriggered = onSearchQueryTriggered,
                    hasMultipleVersions = hasMultipleVersions,
                    onOpenVersions = onOpenVersions,
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
                    resumeEpisodeFocus = resumeEpisodeFocus,
                    resumeEpisodeIndex = resumeEpisodeIndex,
                    emptyEpisodesFocus = emptyEpisodesFocus,
                    episodeListState = episodeListState,
                    seasonFocus = { number -> seasonRequesters.getOrPut(number) { FocusRequester() } },
                    seasonRowState = seasonRowState,
                    onSeasonFocused = { number ->
                        if (number != selectedSeason) selectedSeason = number
                    },
                    onEpisodeClick = onEpisodeClick,
                    onBackToHero = {
                        section = tvSeriesSectionAfter(
                            section,
                            TvSeriesDetailsTransition.EPISODES_TO_HERO,
                            relatedSeries.isNotEmpty()
                        )
                    },
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

                // L'amorce de la rangée est visible en permanence : la faire
                // apparaître à l'atteinte du dernier épisode surgissait dans un
                // vide qui n'annonçait rien. Seule son **ouverture** — le
                // défilement qui l'amène à l'écran — reste conditionnée à une
                // descente depuis le dernier épisode.
                if (relatedSeries.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 48.dp, end = 48.dp, bottom = TV_SERIES_BOTTOM_RESERVE)
                            .onSizeChanged { relatedRowHeightPx = it.height.toFloat() }
                            .onFocusedBoundsChanged { focusedRelatedChild = it }
                            .onFocusChanged {
                                relatedFocused = it.hasFocus
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
        // Posé hors de la colonne translatée : l'indice reste collé au bas de
        // l'écran et s'efface dès qu'on quitte le hero, au lieu de descendre
        // avec le panneau qu'il annonce.
        TvSeriesScrollHint(
            visible = section == TvSeriesDetailsSection.HERO,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        TvFocusSelectorOverlay(focusSelector, modifier = Modifier.fillMaxSize())
    }
}

/** Voir [TV_SERIES_SCROLL_HINT_SIZE] : chevron d'appel vers les épisodes. */
@Composable
private fun TvSeriesScrollHint(visible: Boolean, modifier: Modifier = Modifier) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "seriesScrollHintAlpha"
    )
    if (alpha == 0f) return

    val transition = rememberInfiniteTransition(label = "seriesScrollHint")
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = TV_SERIES_SCROLL_HINT_PERIOD_MS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "seriesScrollHintBob"
    )
    val travelPx = with(LocalDensity.current) { TV_SERIES_SCROLL_HINT_TRAVEL.toPx() }

    Icon(
        imageVector = Icons.Default.KeyboardArrowDown,
        // Décoratif : l'action est déjà portée par la navigation D-pad, et un
        // lecteur d'écran n'a que faire d'un repère purement visuel.
        contentDescription = null,
        tint = TextSecondary,
        modifier = modifier
            .padding(bottom = TV_SERIES_BOTTOM_RESERVE)
            .size(TV_SERIES_SCROLL_HINT_SIZE)
            .graphicsLayer {
                this.alpha = alpha
                translationY = bob * travelPx
            }
    )
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
    ageRating: Int?,
    isRatingSaving: Boolean,
    onToggleFavorite: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onSearchQueryTriggered: (String) -> Unit,
    hasMultipleVersions: Boolean = false,
    onOpenVersions: () -> Unit = {},
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
            TvSeriesMetadata(details, ageRating)
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
            // Les trois actions se partagent la largeur à parts égales au lieu
            // de se serrer à gauche : la rangée s'aligne sur le bouton de
            // lecture et le synopsis, qui occupent toute la colonne, et le
            // cadre de focus couvre un tiers entier.
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                TvSeriesAction(Icons.Default.Star, stringResource(if (isFavorite) R.string.vod_details_remove_favorite else R.string.vod_details_add_favorite), if (isFavorite) FavoriteGold else TextPrimary, isFavorite, active, onToggleFavorite, Modifier.weight(1f))
                TvSeriesActionDivider()
                TvSeriesAction(Icons.Default.ThumbUp, stringResource(R.string.media_rating_like), if (mediaRating == MediaRatingValue.LIKE) RatingLike else TextPrimary, mediaRating == MediaRatingValue.LIKE, active && !isRatingSaving, onLike, Modifier.weight(1f))
                TvSeriesActionDivider()
                TvSeriesAction(Icons.Default.ThumbDown, stringResource(R.string.media_rating_dislike), if (mediaRating == MediaRatingValue.DISLIKE) RatingDislike else TextPrimary, mediaRating == MediaRatingValue.DISLIKE, active && !isRatingSaving, onDislike, Modifier.weight(1f))
            }
            Spacer(Modifier.height(18.dp))
            val episode = playbackTarget.episode
            val label = if (playbackTarget.isResume && episode != null) {
                stringResource(R.string.series_details_resume_episode, EpisodeLabel.format(episode.seasonNum, episode.episodeNum).orEmpty())
            } else stringResource(R.string.series_details_play)
            // F39 §8.6 (évolution PO) : chevron de version accolé au bouton de lecture, jamais un
            // bouton « Versions » séparé (retour PO) — plus de trio à réaligner sous les actions.
            TvSeriesPlayButtonWithVersionsChevron(
                text = label,
                onClick = onPlay,
                active = active,
                onMoveDown = onMoveToEpisodes,
                hasMultipleVersions = hasMultipleVersions,
                onOpenVersions = onOpenVersions,
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
private fun TvSeriesMetadata(details: SeriesDetails, ageRating: Int?) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        formatReleaseYear(details.releaseDate).takeIf { it.isNotBlank() }?.let {
            Text(it, color = AccentLavande, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = HankenGrotesk)
        }
        details.genre?.takeIf { it.isNotBlank() }?.let {
            TvSeriesMetadataSeparator()
            Text(
                it,
                color = TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .wrapContentWidth(Alignment.Start),
                fontFamily = HankenGrotesk
            )
        }
        details.rating?.takeIf { it.isNotBlank() && it != "0" && it != "0.0" }?.let {
            TvSeriesMetadataSeparator()
            Icon(Icons.Default.Star, null, tint = FavoriteGold, modifier = Modifier.size(15.dp))
            Text(it, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = HankenGrotesk)
        }
        ageRating?.let {
            TvSeriesMetadataSeparator()
            Text(it.displayLabel(), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = HankenGrotesk)
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .focusProperties { canFocus = active }
            .onFocusChanged { focused = it.isFocused }
            .background(if (focused) AccentLavande.copy(alpha = 0.15f) else Color.Transparent)
            .border(1.5.dp, if (focused) AccentLavande else Color.Transparent, RoundedCornerShape(20.dp))
            .clickable(enabled = active) { onClick() }
            .padding(horizontal = 11.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text(text, color = if (selected) tint else TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(14.dp)
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, color = TextPrimary, fontFamily = HankenGrotesk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

/**
 * F39 §8.6 (évolution PO) : [TvSeriesPlayButton] accolé à un chevron ouvrant
 * le sélecteur de versions (dialogue plein écran existant, `onOpenVersions`
 * — jamais `DropdownMenu`, focus D-pad pas fiable sur TV, voir
 * `TvCategoryPicker`). Retombe sur [TvSeriesPlayButton] plein plat sans
 * chevron à 0/1 candidate. Le chevron répond aussi à la touche Bas
 * ([onMoveDown]), comme le bouton de lecture.
 */
@Composable
private fun TvSeriesPlayButtonWithVersionsChevron(
    text: String,
    onClick: () -> Unit,
    active: Boolean,
    onMoveDown: () -> Unit,
    hasMultipleVersions: Boolean,
    onOpenVersions: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!hasMultipleVersions) {
        TvSeriesPlayButton(
            text = text,
            onClick = onClick,
            active = active,
            onMoveDown = onMoveDown,
            modifier = modifier.fillMaxWidth()
        )
        return
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        TvSeriesPlayButton(
            text = text,
            onClick = onClick,
            active = active,
            onMoveDown = onMoveDown,
            shape = RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp, topEnd = 0.dp, bottomEnd = 0.dp),
            modifier = modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(52.dp)
                .background(Color.Black.copy(alpha = 0.22f))
        )
        var focused by remember { mutableStateOf(false) }
        val chevronShape = RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp, topStart = 0.dp, bottomStart = 0.dp)
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(52.dp)
                .clip(chevronShape)
                .focusProperties { canFocus = active }
                .onFocusChanged { focused = it.isFocused }
                .background(if (focused) AccentLavandeHover else AccentLavande)
                .border(2.dp, if (focused) AccentLavandeHover else Color.Transparent, chevronShape)
                .clickable(enabled = active) { onOpenVersions() }
                .onKeyEvent { event ->
                    if (active && event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                        onMoveDown()
                        true
                    } else false
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(R.string.player_versions_action_label),
                tint = TextPrimary
            )
        }
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
    resumeEpisodeFocus: FocusRequester,
    resumeEpisodeIndex: Int?,
    emptyEpisodesFocus: FocusRequester,
    episodeListState: LazyListState,
    seasonFocus: (Int) -> FocusRequester,
    seasonRowState: LazyListState,
    onSeasonFocused: (Int) -> Unit,
    onEpisodeClick: (SeriesEpisode) -> Unit,
    onBackToHero: () -> Unit,
    onOpenRelated: () -> Boolean,
    modifier: Modifier = Modifier
) {
    // Le changement de saison repart en tête ; l'entrée dans le panneau, elle,
    // vise l'épisode repris (voir tvSeriesInitialEpisodeIndex) et défile
    // elle-même — d'où un état hissé, piloté des deux côtés.
    LaunchedEffect(selectedSeason) { episodeListState.scrollToItem(0) }
    Column(modifier = modifier.padding(horizontal = 48.dp, vertical = 18.dp)) {
        Text(stringResource(R.string.series_details_seasons_label), color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
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
            LazyRow(state = seasonRowState, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
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
                            .padding(horizontal = 16.dp, vertical = 7.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.series_details_episodes_label), color = AccentLavande, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (selectedEpisodes.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.series_details_no_episodes_for_season), color = TextSecondary)
            }
        } else {
            LazyColumn(
                state = episodeListState,
                verticalArrangement = Arrangement.spacedBy(TV_SERIES_EPISODE_SPACING),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                itemsIndexed(selectedEpisodes, key = { _, episode -> episode.id }) { index, episode ->
                    val slot = tvSeriesEpisodeSlot(index, selectedEpisodes.lastIndex, resumeEpisodeIndex)
                    TvSeriesEpisodeCard(
                        episode = episode,
                        active = active,
                        modifier = Modifier
                            .then(
                                when (slot) {
                                    TvSeriesEpisodeSlot.FIRST -> Modifier.focusRequester(firstEpisodeFocus)
                                    TvSeriesEpisodeSlot.LAST -> Modifier.focusRequester(lastEpisodeFocus)
                                    TvSeriesEpisodeSlot.RESUME -> Modifier.focusRequester(resumeEpisodeFocus)
                                    TvSeriesEpisodeSlot.NONE -> Modifier
                                }
                            )
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
    val progress = tvSeriesEpisodeProgress(episode)
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
            .padding(8.dp)
    ) {
        Box(
            Modifier
                .size(width = TV_SERIES_EPISODE_THUMB_WIDTH, height = TV_SERIES_EPISODE_THUMB_HEIGHT)
                .clip(RoundedCornerShape(8.dp))
                .background(Surface1),
            contentAlignment = Alignment.Center
        ) {
            if (!episode.movieImage.isNullOrBlank()) AsyncImage(episode.movieImage, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Default.PlayArrow, null, tint = TextSecondary, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("${episode.episodeNum.toString().padStart(2, '0')}  ${episode.title}", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            episode.plot.takeIf { it.isNotBlank() }?.let { Text(it, color = TextSecondary, fontSize = 11.5.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)) }
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(130.dp)) {
            val statusText = when (tvSeriesEpisodeStatus(episode)) {
                TvSeriesEpisodeStatus.WATCHED -> stringResource(R.string.series_details_watched)
                TvSeriesEpisodeStatus.REMAINING ->
                    stringResource(R.string.series_details_resume_info, tvSeriesRemainingDuration(episode).orEmpty())
                TvSeriesEpisodeStatus.DURATION -> episode.duration
                TvSeriesEpisodeStatus.NONE -> null
            }
            statusText?.let {
                Text(text = it, color = if (episode.watched) AccentLavande else TextSecondary, fontSize = 12.sp)
            }
            if (progress > 0f) {
                Spacer(Modifier.height(6.dp))
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
