package com.cstv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.cstv.app.data.local.entity.ExternalCatalogLinkEntity

@Dao
interface ExternalCatalogLinkDao {
    /**
     * Résolution batch (T24) : une requête indexée sur `externalId` pour
     * toute une page Trending/Popular, plutôt qu'un rematching complet du
     * catalogue. `@Upsert` : un item déjà associé et rematché (ex. après
     * resync) met simplement à jour `updatedAt` plutôt que d'échouer.
     */
    @Query("SELECT * FROM external_catalog_links WHERE externalId IN (:externalIds)")
    suspend fun findByExternalIds(externalIds: List<String>): List<ExternalCatalogLinkEntity>

    @Upsert
    suspend fun upsert(link: ExternalCatalogLinkEntity)

    @Upsert
    suspend fun upsertAll(links: List<ExternalCatalogLinkEntity>)

    /**
     * Purge des associations dont le média n'existe plus dans le catalogue
     * local (`CatalogReconciler`, T20 §4.5) : borne la fenêtre pendant
     * laquelle un `providerId` réattribué par le panel Xtream à une autre
     * œuvre pourrait hériter à tort d'un ancien `externalId`.
     */
    @Query(
        "DELETE FROM external_catalog_links WHERE NOT (" +
            "(kind = 'movie' AND providerId IN (SELECT streamId FROM vod_streams)) OR " +
            "(kind = 'series' AND providerId IN (SELECT seriesId FROM series_streams)))"
    )
    suspend fun purgeOrphaned()
}
