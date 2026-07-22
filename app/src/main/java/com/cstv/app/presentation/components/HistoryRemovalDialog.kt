package com.cstv.app.presentation.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cstv.app.R

@Composable
fun HistoryRemovalDialog(
    contentName: String,
    isTv: Boolean,
    isRemoving: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val cancelFocusRequester = remember { FocusRequester() }
    LaunchedEffect(isTv) {
        if (isTv) cancelFocusRequester.requestFocus()
    }
    AlertDialog(
        onDismissRequest = { if (!isRemoving) onDismiss() },
        title = { Text(stringResource(R.string.history_removal_title)) },
        text = { Text(stringResource(R.string.history_removal_message, contentName)) },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !isRemoving) {
                if (isRemoving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else Text(stringResource(R.string.history_removal_confirm))
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                enabled = !isRemoving,
                modifier = if (isTv) Modifier.focusRequester(cancelFocusRequester) else Modifier
            ) { Text(stringResource(R.string.history_removal_cancel)) }
        }
    )
}
