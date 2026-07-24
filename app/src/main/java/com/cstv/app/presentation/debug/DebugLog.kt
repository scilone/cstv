package com.cstv.app.presentation.debug

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Buffer de logs en mémoire, observable par Compose, pour diagnostiquer sans
 * câble/adb (l'écran Paramètres > Journal de debug l'affiche et permet la copie).
 * Chaque ligne est aussi miroir vers logcat. Anneau borné à [MAX_LINES].
 */
object DebugLog {

    private const val MAX_LINES = 500
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    fun log(tag: String, message: String) {
        Log.w(tag, message)
        val line = "${timeFormat.format(Date())} [$tag] $message"
        synchronized(this) {
            val next = (_lines.value + line)
            _lines.value = if (next.size > MAX_LINES) next.takeLast(MAX_LINES) else next
        }
    }

    fun clear() {
        _lines.value = emptyList()
    }

    /** Texte complet, une ligne par entrée, pour la copie presse-papiers. */
    fun dump(): String = _lines.value.joinToString("\n")
}
