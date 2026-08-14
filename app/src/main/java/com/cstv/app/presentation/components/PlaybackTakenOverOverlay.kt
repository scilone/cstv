package com.cstv.app.presentation.components

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import com.cstv.app.R

@Composable
fun PlaybackTakenOverOverlay(deviceName: String?, onResume: () -> Unit, onDismiss: () -> Unit) {
    val name = deviceName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.playback_lock_other_device)
    val resumeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { resumeFocus.requestFocus() }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playback_lock_taken_title)) },
        text = { Text(stringResource(R.string.playback_lock_taken_message, name)) },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_back)) } },
        confirmButton = { Button(onClick = onResume, modifier = Modifier.focusRequester(resumeFocus)) { Text(stringResource(R.string.playback_lock_resume)) } },
    )
}
