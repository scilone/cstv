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
 *
 * Seule une pression **neuve** (`repeatCount == 0`) apparie un KeyUp. La
 * fenêtre s'ouvrant pendant que la touche est encore enfoncée, Android
 * continue d'y livrer les KeyDown de répétition de cette même pression : les
 * compter comme un appui légitime rendait le KeyUp de fermeture indiscernable
 * d'un vrai clic, et la boîte se refermait toujours d'elle-même.
 */
class ActivationKeyGate {
    private var sawKeyDown = false

    /** @param isRepeat KeyDown de répétition — appartient à une pression commencée ailleurs. */
    fun onKeyDown(isRepeat: Boolean) {
        if (!isRepeat) sawKeyDown = true
    }

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
        val native = event.nativeKeyEvent
        if (native.keyCode != AndroidKeyEvent.KEYCODE_DPAD_CENTER &&
            native.keyCode != AndroidKeyEvent.KEYCODE_ENTER
        ) {
            return@onPreviewKeyEvent false
        }
        when (event.type) {
            KeyEventType.KeyDown -> {
                gate.onKeyDown(isRepeat = native.repeatCount > 0 || native.isLongPress)
                // Une répétition héritée est aussi consommée : la laisser
                // passer réarmerait l'interaction de pression de `clickable`,
                // qui déclencherait alors le clic sur le KeyUp suivant.
                native.repeatCount > 0 || native.isLongPress
            }
            KeyEventType.KeyUp -> gate.onKeyUp()
            else -> false
        }
    }
}
