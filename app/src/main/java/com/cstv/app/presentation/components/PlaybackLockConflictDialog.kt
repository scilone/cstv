package com.cstv.app.presentation.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import com.cstv.app.R
import com.cstv.app.domain.model.PlaybackLockHolder

@Composable
fun PlaybackLockConflictDialog(holder: PlaybackLockHolder, onDismiss: () -> Unit, onTakeOver: () -> Unit) {
    val name = holder.deviceName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.playback_lock_other_device)
    val duration = if (holder.heldForSeconds < 60L) stringResource(R.string.playback_lock_just_now) else stringResource(R.string.playback_lock_minutes, holder.heldForSeconds / 60L)
    val takeoverFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { takeoverFocus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playback_lock_conflict_title)) },
        text = { Text(stringResource(R.string.playback_lock_conflict_message, name, duration)) },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.playback_lock_cancel)) } },
        confirmButton = { TextButton(onClick = onTakeOver, modifier = Modifier.focusRequester(takeoverFocus)) { Text(stringResource(R.string.playback_lock_takeover)) } },
    )
}
