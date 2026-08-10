package com.cstv.app.data.local.storage

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Détient l'identifiant du profil actif (Phase 27). Exposé en StateFlow pour
 * rafraîchir en direct les écrans Favoris/Historique/Continuer à regarder au
 * changement de profil, sans redémarrage.
 *
 * Interface (mockable en test unitaire) ; l'implémentation stocke la valeur en
 * SharedPreferences simples — ce n'est pas une donnée sensible.
 */
interface ProfileManager {
    val activeProfileId: StateFlow<Int>
    fun currentProfileId(): Int
    fun setActiveProfileId(id: Int)

    val autoStartProfileId: StateFlow<Int>
    fun currentAutoStartProfileId(): Int
    fun setAutoStartProfileId(id: Int)

    companion object {
        const val NO_PROFILE = -1
    }
}

class ProfileManagerImpl(context: Context) : ProfileManager {

    private val prefs = context.getSharedPreferences("profile_prefs", Context.MODE_PRIVATE)

    private val _activeProfileId = MutableStateFlow(prefs.getInt(KEY_ACTIVE_PROFILE, ProfileManager.NO_PROFILE))
    override val activeProfileId: StateFlow<Int> = _activeProfileId.asStateFlow()

    override fun currentProfileId(): Int = _activeProfileId.value

    override fun setActiveProfileId(id: Int) {
        prefs.edit().putInt(KEY_ACTIVE_PROFILE, id).apply()
        _activeProfileId.value = id
    }

    private val _autoStartProfileId = MutableStateFlow(prefs.getInt(KEY_AUTO_START_PROFILE, ProfileManager.NO_PROFILE))
    override val autoStartProfileId: StateFlow<Int> = _autoStartProfileId.asStateFlow()

    override fun currentAutoStartProfileId(): Int = _autoStartProfileId.value

    override fun setAutoStartProfileId(id: Int) {
        prefs.edit().putInt(KEY_AUTO_START_PROFILE, id).apply()
        _autoStartProfileId.value = id
    }

    companion object {
        private const val KEY_ACTIVE_PROFILE = "active_profile_id"
        private const val KEY_AUTO_START_PROFILE = "auto_start_profile_id"
    }
}
