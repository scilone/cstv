package com.cstv.app.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Retour utilisateur 2026-08-21 : sans ce battement, la convergence du backfill (§8.11) ne
 * progressait que par rafales liées aux ouvertures d'appli — `resumeExternalMetadataHydration()`
 * n'enqueue qu'un seul passage au démarrage, et `ExternalMetadataHydrationWorker` ne se
 * reprogramme lui-même que jusqu'à l'échéance du prochain item en file (potentiellement des
 * heures de silence si tout est en backoff).
 *
 * Volontairement un worker séparé plutôt que planifier `ExternalMetadataHydrationWorker`
 * directement en périodique : celui-ci tourne sous `enqueueUniqueWork`/`ExistingWorkPolicy.KEEP`
 * (§8.11 "une seule hydratation active à la fois"). Une `PeriodicWorkRequest` sous un nom unique
 * différent contournerait cette exclusion et romprait la garantie "jamais en parallèle" — ce relai
 * se contente de retaper le même point d'entrée `enqueue()`, qui reste un no-op si une exécution
 * est déjà en cours ou programmée.
 */
class ExternalMetadataHydrationHeartbeatWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        ExternalMetadataHydrationWorker.enqueue(applicationContext)
        return Result.success()
    }
}
