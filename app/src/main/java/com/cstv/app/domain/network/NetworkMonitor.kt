package com.cstv.app.domain.network

import kotlinx.coroutines.flow.StateFlow

/**
 * Connectivité de transport observable.
 *
 * Interface et non classe concrète : [isCurrentlyOnline] retourne un `Boolean`
 * primitif, et un mock de classe Kotlin sur retour primitif provoque un NPE
 * d'unboxing avec la configuration Mockito du projet (même motif que
 * `ProfileManager`/`ProfileManagerImpl`, voir AGENTS.md).
 *
 * Le signal est optimiste : un réseau actif ne garantit pas que le panel
 * Xtream réponde. L'état hors ligne réellement affiché combine cette valeur
 * avec le dernier échec de synchronisation.
 */
interface NetworkMonitor {
    val isOnline: StateFlow<Boolean>
    fun isCurrentlyOnline(): Boolean
}
