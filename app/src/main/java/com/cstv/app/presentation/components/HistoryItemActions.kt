package com.cstv.app.presentation.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import com.cstv.app.R

/** Keeps a normal activation intact while exposing a history action on long press. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.historyItemActions(
    isTv: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?
): Modifier = composed {
    if (onLongClick == null) return@composed clickable(onClick = onClick)
    val removeLabel = stringResource(R.string.history_removal_confirm)
    val accessibilityActions = Modifier.semantics {
        customActions = listOf(CustomAccessibilityAction(removeLabel) {
            onLongClick()
            true
        })
    }
    if (!isTv) return@composed accessibilityActions.combinedClickable(
        onClick = onClick,
        onLongClick = onLongClick,
        onLongClickLabel = removeLabel
    )

    var consumeKeyUp by remember { mutableStateOf(false) }
    accessibilityActions.onPreviewKeyEvent { event ->
        val native = event.nativeKeyEvent
        val isCenter = native.keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER || native.keyCode == AndroidKeyEvent.KEYCODE_ENTER
        if (!isCenter) return@onPreviewKeyEvent false
        when (event.type) {
            KeyEventType.KeyDown -> {
                if (native.isLongPress || native.repeatCount > 0) {
                    if (!consumeKeyUp) onLongClick()
                    consumeKeyUp = true
                    true
                } else false
            }
            KeyEventType.KeyUp -> {
                if (consumeKeyUp) {
                    consumeKeyUp = false
                    true
                } else false
            }
            else -> false
        }
    }.clickable(onClick = onClick)
}
