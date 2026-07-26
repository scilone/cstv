package com.cstv.app.domain.usecase

import com.cstv.app.domain.sync.CatalogStatus
import com.cstv.app.domain.sync.CatalogSyncManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Point d'entrée des écrans vers la fraîcheur du catalogue (bannière hors
 * ligne, état de premier démarrage sans cache). Façade mince : la composition
 * de l'état appartient au [CatalogSyncManager], pour que l'UI n'ait qu'une
 * seule source à observer.
 */
class ObserveCatalogStatusUseCase @Inject constructor(
    private val catalogSyncManager: CatalogSyncManager
) {
    operator fun invoke(): Flow<CatalogStatus> = catalogSyncManager.catalogStatus
}
