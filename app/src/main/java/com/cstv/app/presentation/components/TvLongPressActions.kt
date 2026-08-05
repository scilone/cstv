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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics

/**
 * Keeps a normal activation intact while exposing a secondary action on long
 * press (F25) — extraction générique de `historyItemActions`, dont l'appariement
 * KeyDown/KeyUp corrigé par B20 est conservé tel quel : sans lui, un déplacement
 * de D-Pad pendant l'appui long laisserait `consumeKeyUp` bloqué.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tvLongPressActions(
    isTv: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    longClickLabel: String
): Modifier = composed {
    if (onLongClick == null) return@composed clickable(onClick = onClick)
    val accessibilityActions = Modifier.semantics {
        customActions = listOf(CustomAccessibilityAction(longClickLabel) {
            onLongClick()
            true
        })
    }
    if (!isTv) return@composed accessibilityActions.combinedClickable(
        onClick = onClick,
        onLongClick = onLongClick,
        onLongClickLabel = longClickLabel
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
    }.onFocusChanged { if (!it.isFocused) consumeKeyUp = false }
        .clickable(onClick = onClick)
}
