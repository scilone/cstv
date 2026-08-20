package com.cstv.app.data.worker

import android.content.Context
import com.cstv.app.data.local.dao.ExternalMetadataDao
import com.cstv.app.data.local.entity.ExternalHydrationRequestEntity
import com.cstv.app.domain.model.HydrationReason
import com.cstv.app.domain.util.TimeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F45 §7.5/§8.11 : point d'entrée unique des trois producteurs (ouverture de fiche, nouveaux
 * médias après sync, backfill/rattrapage) vers la file d'hydratation Room. Dédupliquée par
 * `(kind, providerId)` (clé primaire de `external_hydration_queue`) : une demande déjà en file à
 * priorité égale ou supérieure n'est jamais rétrogradée ni dupliquée — seule une priorité
 * strictement supérieure (typiquement `DETAIL_OPEN` sur une entrée `MISSING_METADATA`) la promeut.
 *
 * F45-R12 : la lecture de la priorité existante et l'upsert sont regroupées dans une seule
 * transaction Room côté DAO (`upsertRequestIfHigherPriority`) plutôt que deux appels séparés ici —
 * un `SELECT` puis un `UPSERT` distincts laissaient une demande de fond concurrente écraser une
 * promotion `DETAIL_OPEN` décidée entre-temps.
 */
@Singleton
class ExternalMetadataHydrationScheduler @Inject constructor(
    private val dao: ExternalMetadataDao,
    private val timeProvider: TimeProvider,
    @ApplicationContext private val context: Context,
) {
    suspend fun request(kind: String, providerId: Int, reason: HydrationReason) {
        requestBatch(kind, listOf(providerId), reason)
    }

    /** F45-R6 : une seule transaction Room et un seul réveil WorkManager pour tout le lot. */
    suspend fun requestBatch(kind: String, providerIds: Collection<Int>, reason: HydrationReason) {
        if (providerIds.isEmpty()) return
        val now = timeProvider.nowMillis()
        dao.upsertRequestsIfHigherPriority(
            providerIds.distinct().map { providerId ->
                ExternalHydrationRequestEntity(
                    kind = kind, providerId = providerId, reason = reason.wireValue,
                    priority = reason.priority, createdAt = now, nextAttemptAt = now, attemptCount = 0,
                )
            },
        )
        ExternalMetadataHydrationWorker.enqueue(context)
    }
}
