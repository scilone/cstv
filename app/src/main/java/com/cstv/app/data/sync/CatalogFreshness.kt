package com.cstv.app.data.sync

import com.cstv.app.data.local.dao.CatalogSyncStateDao
import com.cstv.app.domain.sync.CatalogSection
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
    // maxOf(...) : l'enrichissement (années de sortie notamment) est estampillé
    // après les sections catalogue de la même synchronisation (B14). Sans lui,
    // un cache d'appariement TMDB écrit avant la fin de l'enrichissement reste
    // valide indéfiniment et fige un mauvais rapprochement (remake/homonyme).
    open suspend fun vodSyncedAt(): Long =
        maxOf(syncedAt(CatalogSection.VOD_STREAMS), syncedAt(CatalogSection.ENRICHMENT))

    open suspend fun seriesSyncedAt(): Long =
        maxOf(syncedAt(CatalogSection.SERIES_STREAMS), syncedAt(CatalogSection.ENRICHMENT))

    private suspend fun syncedAt(section: String): Long =
        try {
            syncStateDao.getSection(section)?.lastSuccessAt ?: 0L
        } catch (e: Exception) {
            // Une estampille illisible doit invalider le cache, pas faire
            // échouer l'écran qui la consulte.
            0L
        }
}
