package com.cstv.app.presentation.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.res.stringResource
import com.cstv.app.R

/** Keeps a normal activation intact while exposing a history action on long press. */
fun Modifier.historyItemActions(
    isTv: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?
): Modifier = composed {
    tvLongPressActions(isTv, onClick, onLongClick, stringResource(R.string.history_removal_confirm))
}
