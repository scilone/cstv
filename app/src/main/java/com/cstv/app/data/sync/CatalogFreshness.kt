package com.cstv.app.data.sync

import com.cstv.app.data.local.dao.CatalogSyncStateDao
import com.cstv.app.data.local.entity.CatalogSection
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Date de dernière synchronisation réussie d'une section, lue depuis
 * `catalog_sync_state`.
 *
 * Sert d'estampille de génération aux caches TMDB (tendances et top 10) : leur
 * appariement titre ↔ catalogue devient caduc dès que le catalogue change.
 * Avant T4, ces caches s'appuyaient sur trois clés `SharedPreferences` en
 * clair ; la fraîcheur n'a désormais qu'une seule source.
 */
// `open` : le projet n'a pas `mockito-inline` (AGENTS.md), et les tests des
// caches TMDB doivent pouvoir piloter l'estampille de génération.
@Singleton
open class CatalogFreshness @Inject constructor(
    private val syncStateDao: CatalogSyncStateDao
) {
    open suspend fun vodSyncedAt(): Long = syncedAt(CatalogSection.VOD_STREAMS)

    open suspend fun seriesSyncedAt(): Long = syncedAt(CatalogSection.SERIES_STREAMS)

    private suspend fun syncedAt(section: String): Long =
        try {
            syncStateDao.getSection(section)?.lastSuccessAt ?: 0L
        } catch (e: Exception) {
            // Une estampille illisible doit invalider le cache, pas faire
            // échouer l'écran qui la consulte.
            0L
        }
}
