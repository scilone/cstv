package com.cstv.app.presentation.home.components
import com.cstv.app.R
import androidx.compose.ui.res.stringResource
import com.cstv.app.domain.model.EpisodeLabel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.cstv.app.presentation.components.tvFocusHighlight
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
import com.cstv.app.domain.model.mediaVersionBadges
import com.cstv.app.presentation.theme.AccentLavande
import com.cstv.app.presentation.theme.DarkBackground
import com.cstv.app.presentation.theme.Surface1
import com.cstv.app.presentation.components.historyItemActions
import com.cstv.app.presentation.components.tvLongPressActions
import com.cstv.app.presentation.theme.FavoriteGold
import com.cstv.app.presentation.theme.Surface2
import com.cstv.app.presentation.theme.Surface3
import com.cstv.app.presentation.theme.BricolageGrotesque
import com.cstv.app.presentation.theme.HankenGrotesk

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

    // Phase 55 : ligne meta = "S01E03 · {temps restant}" en accent, sous le titre.
    // B31 : PlaybackPosition.type vaut "movie" ou "episode" (jamais "series").
    val metaText = buildString {
        if (position.type == "episode") {
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
            .tvFocusHighlight(isFocused, RoundedCornerShape(12.dp))
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
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String = "",
    isTv: Boolean = false,
    modifier: Modifier = Modifier.width(130.dp)
) {
    var isFocused by remember { mutableStateOf(false) }

    // Phase 55 : titre en overlay dans la vignette (maquette), plus en dessous.
    // Cette carte n'apparaît que dans des listes déjà filtrées aux favoris —
    // l'appui long y retire donc toujours, jamais n'ajoute (hotfix retour PO).
    Box(
        modifier = modifier
            .height(195.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .tvFocusHighlight(isFocused, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .tvLongPressActions(isTv, onClick, onLongClick, longClickLabel)
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
            .tvFocusHighlight(
                focused = isFocused,
                shape = RoundedCornerShape(16.dp),
                restingColor = Color.White.copy(alpha = 0.07f),
                restingWidth = 1.dp
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
    /**
     * Libellé de l'action d'appui long, lu par l'accessibilité et par
     * l'action personnalisée TalkBack. Le défaut couvre l'usage historique de la
     * carte (retrait de la rangée « Reprendre ») ; les catalogues passent le
     * libellé « favoris ».
     */
    longClickLabel: String = stringResource(R.string.history_removal_confirm),
    /** Étoile en surimpression dès que le média est dans les favoris. */
    isFavorite: Boolean = false,
    isTv: Boolean = false,
    /**
     * `true` en cellule de grille (`LazyVerticalGrid`) : la carte occupe toute
     * la largeur imposée par `GridCells` et dérive sa hauteur du ratio 2:3.
     * `false` (défaut) : dimensions fixes de rangée (130 × 195 dp), inchangées
     * pour les sept appels existants de la Home (B18).
     */
    fillCell: Boolean = false,
    /** Badge court en surimpression, coin haut-gauche de l'affiche (ex. « S01E03 »). */
    badgeLabel: String? = null,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val rankWidth = if (rank == 10) 112.dp else 74.dp
    val cardWidth = if (rank == null) 130.dp else 130.dp + rankWidth - 30.dp
    val sizeModifier = if (fillCell) {
        Modifier.fillMaxWidth().aspectRatio(2f / 3f)
    } else {
        Modifier.width(cardWidth).height(195.dp) // standard 2:3 ratio
    }
    val posterModifier = modifier.then(if (fillCell) Modifier.fillMaxSize() else Modifier.width(130.dp).fillMaxHeight())
        .onFocusChanged { isFocused = it.isFocused }
        .tvFocusHighlight(isFocused, RoundedCornerShape(14.dp))
        .clip(RoundedCornerShape(14.dp))
        .background(Surface1)
        // Le rang Top 10 reste décoratif : seule l'affiche porte le focus et
        // fournit donc ses bounds exacts au sélecteur pivot.
        .tvLongPressActions(isTv, onClick, onLongClick, longClickLabel)

    Box(
        modifier = sizeModifier,
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

            if (!badgeLabel.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(badgeLabel, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }

            // F39 : langue puis qualité (décision étape 1 — jamais masqué, y compris sur une
            // rangée Top 10 : correction F39-R6). Le rang Top 10 occupe tout le coin bas-gauche
            // (TopRankBadge, numéral jusqu'à 158dp de haut) : sur ces rangées, le badge de version
            // se replie en haut, centré entre la note et l'étiquette déjà présentes, plutôt que de
            // disparaître ou de se superposer illisiblement au numéral.
            val versionLabel = remember(stream.languageTag, stream.qualityTag) {
                mediaVersionBadges(stream.languageTag, stream.qualityTag).takeIf { it.isNotEmpty() }?.joinToString(" · ")
            }
            if (versionLabel != null) {
                val versionBadgeAlignment = when (versionBadgeCorner(rank)) {
                    VersionBadgeCorner.BOTTOM_START -> Alignment.BottomStart
                    VersionBadgeCorner.TOP_CENTER -> Alignment.TopCenter
                }
                HomeCardVersionBadge(versionLabel, Modifier.align(versionBadgeAlignment))
            }

            if (isFavorite) HomeCardFavoriteBadge(Modifier.align(Alignment.BottomEnd))
        }
    }
}

/**
 * Étoile de favori en surimpression.
 *
 * Coin bas-droit : le coin haut-droit porte déjà la note et le coin haut-gauche
 * le repère « S01E03 » des reprises. Purement décorative — l'action reste
 * l'appui long, déjà annoncé par [Modifier.tvLongPressActions].
 */
@Composable
private fun HomeCardFavoriteBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(6.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xCC000000))
            .padding(3.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = FavoriteGold,
            modifier = Modifier.size(12.dp)
        )
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
            .tvFocusHighlight(isFocused, shape)
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
    /** Voir [HomeVodMovieCard]. */
    longClickLabel: String = stringResource(R.string.history_removal_confirm),
    /** Voir [HomeVodMovieCard]. */
    isFavorite: Boolean = false,
    isTv: Boolean = false,
    /** Voir [HomeVodMovieCard] : `true` en cellule de grille (B18). */
    fillCell: Boolean = false,
    /** Badge court en surimpression, coin haut-gauche de l'affiche (ex. « S01E03 »). */
    badgeLabel: String? = null,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val rankWidth = if (rank == 10) 112.dp else 74.dp
    val cardWidth = if (rank == null) 130.dp else 130.dp + rankWidth - 30.dp
    val sizeModifier = if (fillCell) {
        Modifier.fillMaxWidth().aspectRatio(2f / 3f)
    } else {
        Modifier.width(cardWidth).height(195.dp) // standard 2:3 ratio
    }
    val posterModifier = modifier.then(if (fillCell) Modifier.fillMaxSize() else Modifier.width(130.dp).fillMaxHeight())
        .onFocusChanged { isFocused = it.isFocused }
        .tvFocusHighlight(isFocused, RoundedCornerShape(14.dp))
        .clip(RoundedCornerShape(14.dp))
        .background(Surface1)
        // Le rang Top 10 est hors de la cible focusable : le contour ne doit
        // entourer que la vignette, jamais le chiffre.
        .tvLongPressActions(isTv, onClick, onLongClick, longClickLabel)

    Box(
        modifier = sizeModifier,
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

            if (!badgeLabel.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(badgeLabel, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }

            // F39 : voir HomeVodMovieCard — correction F39-R6, jamais masqué sur une rangée Top 10.
            val versionLabel = remember(stream.languageTag, stream.qualityTag) {
                mediaVersionBadges(stream.languageTag, stream.qualityTag).takeIf { it.isNotEmpty() }?.joinToString(" · ")
            }
            if (versionLabel != null) {
                val versionBadgeAlignment = when (versionBadgeCorner(rank)) {
                    VersionBadgeCorner.BOTTOM_START -> Alignment.BottomStart
                    VersionBadgeCorner.TOP_CENTER -> Alignment.TopCenter
                }
                HomeCardVersionBadge(versionLabel, Modifier.align(versionBadgeAlignment))
            }

            if (isFavorite) HomeCardFavoriteBadge(Modifier.align(Alignment.BottomEnd))
        }
    }
}

/**
 * F39-R6 : coin visé par le badge de version selon la présence d'un rang
 * Top 10 — extrait de la Composable en fonction pure pour rester testable en
 * JVM sans dépendance Compose (AGENTS.md, aucun device requis), là où la
 * politique « jamais masqué » se vérifiait mal directement dans l'UI.
 */
internal enum class VersionBadgeCorner { BOTTOM_START, TOP_CENTER }

internal fun versionBadgeCorner(rank: Int?): VersionBadgeCorner =
    if (rank == null) VersionBadgeCorner.BOTTOM_START else VersionBadgeCorner.TOP_CENTER

/**
 * Étiquette langue/qualité (F39), ex. « VF · 4K » — coin bas-gauche, sauf sur
 * les rangées à rang (Top 10) où [TopRankBadge] y pose un numéral jusqu'à
 * 158dp de haut : l'appelant y bascule l'alignement en haut-centre plutôt que
 * de masquer le badge (correction F39-R6 — jamais d'exception au critère
 * d'acceptation des vignettes ; voir [versionBadgeCorner]). Même habillage
 * visuel que [HomeCardFavoriteBadge] et le badge de reprise.
 */
@Composable
private fun HomeCardVersionBadge(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(6.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xCC000000))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(label, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
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
