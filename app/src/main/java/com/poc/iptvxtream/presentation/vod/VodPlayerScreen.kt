package com.poc.iptvxtream.presentation.vod

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.poc.iptvxtream.domain.model.Credentials
import com.poc.iptvxtream.domain.model.TrackPreference
import com.poc.iptvxtream.domain.model.VodDetails
import com.poc.iptvxtream.presentation.player.common.MediaPlayerScreen

/**
 * Player film : wrapper fin autour de [MediaPlayerScreen] (audit #6).
 * Fournit l'URL du flux, les métadonnées et la persistance propre aux films
 * (position de reprise par streamId, préférence de pistes MOVIE — Phase 29).
 */
@Composable
fun VodPlayerScreen(
    details: VodDetails,
    initialPositionMs: Long,
    credentials: Credentials,
    isTv: Boolean,
    viewModel: VodViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Préférence de pistes mémorisée pour CE film (Phase 29). Préchargée une
    // fois ; prioritaire sur le fallback global "dernière langue utilisée".
    // Mise à jour localement à chaque sélection pour que l'auto-application
    // reflète immédiatement le dernier choix.
    var moviePref by remember { mutableStateOf<TrackPreference?>(null) }
    LaunchedEffect(details.streamId) {
        moviePref = viewModel.getMovieTrackPreference(details.streamId)
    }

    MediaPlayerScreen(
        mediaUrl = details.getPlayUrl(
            baseUrl = credentials.baseUrl,
            username = credentials.username,
            password = credentials.password
        ),
        initialPositionMs = initialPositionMs,
        title = details.name,
        subtitleLine = details.genre,
        subtitleStyle = viewModel.getSubtitleStyle(),
        trackPreference = moviePref,
        globalPreferredAudio = { viewModel.getPreferredAudio() },
        globalPreferredSubtitle = { viewModel.getPreferredSubtitle() },
        showLoadingLabel = true,
        onSavePosition = { positionMs, durationMs ->
            viewModel.savePosition(details.streamId, positionMs, durationMs, details)
        },
        onClearPosition = { viewModel.clearPosition(details.streamId) },
        onAudioSelected = { lang ->
            viewModel.saveMovieAudio(details.streamId, lang)
            moviePref = (moviePref ?: TrackPreference(null, null)).copy(audioLang = lang)
        },
        onSubtitleSelected = { lang ->
            viewModel.saveMovieSubtitle(details.streamId, lang)
            moviePref = (moviePref ?: TrackPreference(null, null)).copy(subtitleLang = lang)
        },
        onClose = onClose,
        modifier = modifier
    )
}
