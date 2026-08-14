package com.cstv.app.presentation.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cstv.app.R
import com.cstv.app.presentation.theme.TextPrimary

@Composable
fun HistoryRemovalDialog(
    contentName: String,
    isTv: Boolean,
    isRemoving: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    // Focus initial sur « Annuler » : l'action destructrice ne doit jamais être
    // celle qu'un appui réflexe déclenche.
    val cancelFocus = rememberTvInitialFocus(isTv = isTv, ready = !isRemoving, targetKey = contentName)
    CstvDialog(
        title = stringResource(R.string.history_removal_title),
        message = stringResource(R.string.history_removal_message, contentName),
        isTv = isTv,
        onDismissRequest = { if (!isRemoving) onDismiss() },
        modifier = if (isTv) Modifier.consumeOrphanActivationKeys() else Modifier
    ) {
        CstvDialogAction(
            text = stringResource(R.string.history_removal_cancel),
            onClick = onDismiss,
            enabled = !isRemoving,
            isTv = isTv,
            modifier = if (isTv) Modifier.tvInitialFocusTarget(cancelFocus) else Modifier
        )
        CstvDialogAction(
            text = stringResource(R.string.history_removal_confirm),
            onClick = onConfirm,
            enabled = !isRemoving,
            isTv = isTv,
            primary = true,
            content = if (isRemoving) {
                {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = TextPrimary
                    )
                }
            } else null
        )
    }
}
