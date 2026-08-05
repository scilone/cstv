package com.cstv.app.presentation.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Filtre les activations clavier orphelines (B20).
 *
 * Une fenêtre ouverte par un appui long hérite du KeyUp de la pression qui l'a
 * ouverte : le KeyDown a été reçu par la carte, le KeyUp par le bouton
 * nouvellement focalisé. Sans appariement, `clickable` l'interprète comme un
 * clic et referme la fenêtre aussitôt.
 */
class ActivationKeyGate {
    private var sawKeyDown = false

    fun onKeyDown() { sawKeyDown = true }

    /** @return true si le KeyUp doit être consommé (orphelin). */
    fun onKeyUp(): Boolean {
        if (!sawKeyDown) return true
        sawKeyDown = false
        return false
    }
}

fun Modifier.consumeOrphanActivationKeys(): Modifier = composed {
    val gate = remember { ActivationKeyGate() }
    onPreviewKeyEvent { event ->
        val code = event.nativeKeyEvent.keyCode
        if (code != AndroidKeyEvent.KEYCODE_DPAD_CENTER && code != AndroidKeyEvent.KEYCODE_ENTER) {
            return@onPreviewKeyEvent false
        }
        when (event.type) {
            KeyEventType.KeyDown -> { gate.onKeyDown(); false }
            KeyEventType.KeyUp -> gate.onKeyUp()
            else -> false
        }
    }
}
