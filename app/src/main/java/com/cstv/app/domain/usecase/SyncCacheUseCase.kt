package com.cstv.app.domain.usecase

import com.cstv.app.data.local.storage.CredentialsManager
import com.cstv.app.domain.sync.CatalogSyncManager
import com.cstv.app.domain.sync.SyncFailureKind
import com.cstv.app.domain.sync.SyncOutcome
import com.cstv.app.domain.sync.SyncTrigger
import javax.inject.Inject

enum class SyncCacheResult {
    SUCCESS,
    SKIPPED_NO_CREDENTIALS,
    FAILED_RETRYABLE,
    FAILED_PERMANENT
}

/**
 * Façade mince sur [CatalogSyncManager], conservée parce qu'elle est le point
 * d'entrée `@EntryPoint` Hilt du `DatabaseSyncWorker` et l'appel des Paramètres.
 * L'unicité, l'ordonnancement et la classification des erreurs appartiennent
 * désormais au manager, plus à ce use case.
 */
class SyncCacheUseCase @Inject constructor(
    private val credentialsManager: CredentialsManager,
    private val catalogSyncManager: CatalogSyncManager
) {
    suspend operator fun invoke(trigger: SyncTrigger = SyncTrigger.MANUAL): SyncCacheResult {
        if (credentialsManager.getCredentials() == null) {
            return SyncCacheResult.SKIPPED_NO_CREDENTIALS
        }

        val outcome = catalogSyncManager.syncNow(trigger)
        return when (outcome) {
            is SyncOutcome.Success -> SyncCacheResult.SUCCESS
            // Une synchronisation déjà en cours ou un catalogue frais ne sont
            // pas des échecs : les traiter comme tels ferait retenter le worker
            // pour rien, donc générer le trafic panel que T4 supprime.
            SyncOutcome.AlreadyRunning, SyncOutcome.Skipped -> SyncCacheResult.SUCCESS
            is SyncOutcome.Failure -> when (outcome.kind) {
                SyncFailureKind.NETWORK, SyncFailureKind.UNKNOWN -> SyncCacheResult.FAILED_RETRYABLE
                SyncFailureKind.AUTH,
                SyncFailureKind.PANEL,
                SyncFailureKind.STORAGE,
                SyncFailureKind.PARSE -> SyncCacheResult.FAILED_PERMANENT
            }
        }
    }
}
