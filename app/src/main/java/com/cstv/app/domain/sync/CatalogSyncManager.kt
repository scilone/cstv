package com.cstv.app.domain.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Orchestrateur unique des synchronisations de catalogue.
 *
 * Il décide *quand* synchroniser et dans quel ordre ; ce sont les repositories
 * qui mappent et écrivent. Toutes les garanties d'unicité, d'isolation des
 * échecs et de fraîcheur sont concentrées ici plutôt que dispersées dans les
 * appelants.
 */
interface CatalogSyncManager {

    val syncState: StateFlow<SyncState>

    val catalogStatus: Flow<CatalogStatus>

    /** Ne part que si le catalogue est périmé **et** l'appareil en ligne. */
    suspend fun syncIfStale(): SyncOutcome

    /** Ignore le TTL, respecte l'unicité. */
    suspend fun syncNow(trigger: SyncTrigger): SyncOutcome

    /**
     * Purge du catalogue à la connexion lorsque le compte change.
     * Retourne `true` si une purge a réellement eu lieu.
     */
    suspend fun onAccountAuthenticated(accountKey: String): Boolean
}
