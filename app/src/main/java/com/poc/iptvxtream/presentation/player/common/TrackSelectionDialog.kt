package com.poc.iptvxtream.presentation.player.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poc.iptvxtream.R
import com.poc.iptvxtream.presentation.theme.Surface1
import com.poc.iptvxtream.presentation.theme.Surface3
import com.poc.iptvxtream.presentation.theme.SurfaceFocused
import com.poc.iptvxtream.presentation.theme.TrackSelectedAmber

/**
 * Dialogue de sélection des pistes audio et sous-titres, partagé par les
 * players VOD et Séries (audit #6). `onSubtitleTrackSelected(null)` =
 * désactivation des sous-titres.
 */
@Composable
internal fun TrackSelectionDialog(
    availableAudioTracks: List<TrackInfo>,
    availableSubtitleTracks: List<TrackInfo>,
    onAudioTrackSelected: (TrackInfo) -> Unit,
    onSubtitleTrackSelected: (TrackInfo?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "AUDIO & SOUS-TITRES",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        containerColor = Surface3,
        shape = RoundedCornerShape(16.dp),
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Audio Piste
                Text(stringResource(R.string.player_audio_track_upper), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    availableAudioTracks.forEach { track ->
                        TrackRow(
                            label = stringResource(
                                R.string.player_track_label,
                                track.label ?: "",
                                track.language?.uppercase() ?: stringResource(R.string.common_unknown)
                            ),
                            isSelected = track.isSelected,
                            onSelect = { onAudioTrackSelected(track) }
                        )
                    }
                }

                HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)

                // Subtitle Piste
                Text(stringResource(R.string.player_subtitles_upper), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TrackRow(
                        label = stringResource(R.string.player_track_none),
                        isSelected = availableSubtitleTracks.none { it.isSelected },
                        onSelect = { onSubtitleTrackSelected(null) }
                    )

                    availableSubtitleTracks.forEach { track ->
                        TrackRow(
                            label = stringResource(
                                R.string.player_track_label,
                                track.label ?: "",
                                track.language?.uppercase() ?: stringResource(R.string.common_unknown)
                            ),
                            isSelected = track.isSelected,
                            onSelect = { onSubtitleTrackSelected(track) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(stringResource(R.string.player_close), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun TrackRow(
    label: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = 1.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.DarkGray,
                shape = RoundedCornerShape(8.dp)
            )
            .background(if (isFocused) SurfaceFocused else if (isSelected) TrackSelectedAmber else Surface1)
            .clickable { onSelect() }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = isSelected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
