package com.cstv.app.presentation.player

import android.content.Context
import com.cstv.app.presentation.player.cast.isCastAvailable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider Hilt de disponibilité du Cast (F4 Tâche 1). Délègue à la fonction
 * top-level [isCastAvailable] pour partager la logique avec les Composables des
 * lecteurs sans les forcer à passer par l'injection.
 */
@Singleton
class CastAvailabilityProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun isCastAvailable(): Boolean = isCastAvailable(context)
}
