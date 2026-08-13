package com.cstv.app.data.local.db

import android.content.Context
import android.os.StatFs
import com.cstv.app.data.local.dao.DbMaintenanceDao
import com.cstv.app.di.IptvLog
import javax.inject.Inject

/**
 * Indirection sur `StatFs` : classe finale du framework Android, dont le stub
 * JVM des tests unitaires (sans Robolectric, absent du projet) lève à
 * l'instanciation. La production la construit réellement ; les tests
 * fournissent une valeur fixe.
 */
fun interface DiskSpaceProbe {
    fun availableBytes(path: String): Long
}

/**
 * T20-6 : exécute le `VACUUM` demandé par la migration 27→28 (table
 * `db_maintenance`), hors transaction et hors thread principal — `VACUUM` ne
 * peut pas s'exécuter dans la transaction d'une migration Room.
 *
 * Le compactage n'a lieu que si l'espace libre du volume dépasse la taille
 * actuelle du fichier `.db` (marge nécessaire à `VACUUM`, qui réécrit la base
 * dans un fichier temporaire avant de remplacer l'original). Un échec ou un
 * espace insuffisant laisse la ligne `db_maintenance` en place : la demande
 * est rejouée au prochain démarrage, sans jamais invalider la base ni la
 * migration qui l'a demandée.
 */
class DatabaseMaintenanceRunner @Inject constructor(
    private val context: Context,
    private val database: AppDatabase,
    private val dbMaintenanceDao: DbMaintenanceDao,
    private val diskSpaceProbe: DiskSpaceProbe,
) {
    suspend fun run() {
        val request = dbMaintenanceDao.get(TASK_VACUUM) ?: return

        val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
        if (!dbFile.exists()) {
            // Rien à compacter : la demande n'a plus d'objet.
            dbMaintenanceDao.complete(TASK_VACUUM)
            return
        }

        val freeBytes = runCatching { diskSpaceProbe.availableBytes(dbFile.parent ?: dbFile.path) }.getOrElse {
            IptvLog.d("MAINTENANCE", "Espace libre illisible, VACUUM reporté : ${it.message}")
            return
        }
        if (freeBytes <= dbFile.length()) {
            IptvLog.d(
                "MAINTENANCE",
                "Espace libre insuffisant pour VACUUM (libre=$freeBytes, base=${dbFile.length()}), " +
                    "reporté (demandé à ${request.requestedAt})"
            )
            return
        }

        runCatching { database.openHelper.writableDatabase.execSQL("VACUUM") }
            .onSuccess {
                dbMaintenanceDao.complete(TASK_VACUUM)
                IptvLog.d("MAINTENANCE", "VACUUM terminé")
            }
            .onFailure {
                IptvLog.d("MAINTENANCE", "VACUUM a échoué, retenté au prochain démarrage : ${it.message}")
            }
    }

    private companion object {
        const val TASK_VACUUM = "vacuum"
    }
}
