package com.poc.iptvxtream.presentation.series

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.poc.iptvxtream.R
import com.poc.iptvxtream.domain.model.Credentials
import com.poc.iptvxtream.domain.model.SeriesEpisode
import com.poc.iptvxtream.domain.model.TrackPreference
import com.poc.iptvxtream.presentation.player.common.MediaPlayerScreen

/**
 * Player épisode de série : wrapper fin autour de [MediaPlayerScreen]
 * (audit #6). Fournit l'URL du flux, les métadonnées et la persistance
 * propre aux séries (position de reprise par épisode, préférence de pistes
 * SERIES commune à tous les épisodes — Phase 29).
 */
@Composable
fun SeriesPlayerScreen(
    episode: SeriesEpisode,
    seriesId: Int,
    seriesName: String,
    seriesCover: String?,
    credentials: Credentials,
    isTv: Boolean,
    viewModel: SeriesViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Préférence de pistes mémorisée pour CETTE série (Phase 29, commune à
    // tous les épisodes). Prioritaire sur le fallback global. Mise à jour
    // localement à chaque sélection.
    var seriesPref by remember { mutableStateOf<TrackPreference?>(null) }
    LaunchedEffect(seriesId) {
        seriesPref = viewModel.getSeriesTrackPreference(seriesId)
    }

    MediaPlayerScreen(
        mediaUrl = episode.getPlayUrl(
            baseUrl = credentials.baseUrl,
            username = credentials.username,
            password = credentials.password
        ),
        initialPositionMs = episode.resumePositionMs,
        title = episode.title,
        subtitleLine = stringResource(R.string.player_episode, episode.episodeNum),
        subtitleStyle = viewModel.getSubtitleStyle(),
        trackPreference = seriesPref,
        globalPreferredAudio = { viewModel.getPreferredAudio() },
        globalPreferredSubtitle = { viewModel.getPreferredSubtitle() },
        // Comportement historique : le player séries n'affiche que le spinner
        // (le player VOD ajoute un texte "Chargement du flux…").
        showLoadingLabel = false,
        onSavePosition = { positionMs, durationMs ->
            viewModel.savePosition(episode, positionMs, durationMs, seriesName, seriesCover)
        },
        onClearPosition = { viewModel.clearPosition(episode.id) },
        onAudioSelected = { lang ->
            viewModel.saveSeriesAudio(seriesId, lang)
            seriesPref = (seriesPref ?: TrackPreference(null, null)).copy(audioLang = lang)
        },
        onSubtitleSelected = { lang ->
            viewModel.saveSeriesSubtitle(seriesId, lang)
            seriesPref = (seriesPref ?: TrackPreference(null, null)).copy(subtitleLang = lang)
        },
        onClose = onClose,
        modifier = modifier
    )
}
