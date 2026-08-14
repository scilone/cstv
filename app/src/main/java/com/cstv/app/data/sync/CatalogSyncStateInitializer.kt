package com.cstv.app.data.sync

import com.cstv.app.data.local.dao.CatalogSyncStateDao
import com.cstv.app.data.local.entity.CatalogSyncStateEntity
import com.cstv.app.data.local.storage.CredentialsManager
import com.cstv.app.data.local.storage.SettingsManager
import com.cstv.app.domain.sync.CatalogSection
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reprise des horodatages historiques de `SettingsManager` dans
 * `catalog_sync_state`.
 *
 * La migration SQL ne peut pas lire les `SharedPreferences` : sans cette
 * reprise, toute installation existante repartirait avec un catalogue déclaré
 * jamais synchronisé et déclencherait une resynchronisation complète inutile —
 * exactement le trafic que T4 supprime.
 *
 * Les catégories n'ont jamais eu d'horodatage propre : elles héritent de celui
 * des flux du même type, qui n'a pu être écrit que si les catégories étaient
 * déjà en base.
 */
@Singleton
class CatalogSyncStateInitializer @Inject constructor(
    private val syncStateDao: CatalogSyncStateDao,
    private val settingsManager: SettingsManager,
    private val credentialsManager: CredentialsManager
) {

    private val mutex = Mutex()
    @Volatile private var done = false

    suspend fun ensureInitialized() {
        if (done) return
        mutex.withLock {
            if (done) return
            if (syncStateDao.getAll().isNotEmpty()) {
                done = true
                return
            }

            val credentials = credentialsManager.getCredentials()
            if (credentials == null) {
                // Sans identifiants, aucune clé de compte à attribuer au
                // catalogue : la reprise se fera à la première connexion.
                done = true
                return
            }

            val accountKey = CatalogServerKey.from(credentials)
            val liveAt = settingsManager.getLiveAllStreamsSyncedAt()
            val vodAt = settingsManager.getVodAllStreamsSyncedAt()
            val seriesAt = settingsManager.getSeriesAllStreamsSyncedAt()
            if (liveAt <= 0L && vodAt <= 0L && seriesAt <= 0L) {
                done = true
                return
            }

            syncStateDao.upsertAll(
                listOf(
                    CatalogSection.LIVE_CATEGORIES to liveAt,
                    CatalogSection.LIVE_STREAMS to liveAt,
                    CatalogSection.VOD_CATEGORIES to vodAt,
                    CatalogSection.VOD_STREAMS to vodAt,
                    CatalogSection.SERIES_CATEGORIES to seriesAt,
                    CatalogSection.SERIES_STREAMS to seriesAt
                ).map { (section, at) ->
                    CatalogSyncStateEntity(
                        section = section,
                        accountKey = accountKey,
                        lastSuccessAt = at,
                        lastAttemptAt = at
                    )
                }
            )
            done = true
        }
    }
}
