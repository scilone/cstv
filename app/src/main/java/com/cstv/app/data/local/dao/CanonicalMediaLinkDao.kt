package com.cstv.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.cstv.app.data.local.entity.CanonicalMediaLinkEntity

@Dao
interface CanonicalMediaLinkDao {
    /**
     * Résolution batch (T24) : une requête indexée sur `canonicalId` pour
     * toute une page Trending/Popular, plutôt qu'un rematching complet du
     * catalogue. `@Upsert` : un item déjà associé et rematché (ex. après
     * resync) met simplement à jour `updatedAt` plutôt que d'échouer.
     */
    @Query("SELECT * FROM canonical_media_links WHERE canonicalId IN (:canonicalIds)")
    suspend fun findByCanonicalIds(canonicalIds: List<String>): List<CanonicalMediaLinkEntity>

    @Upsert
    suspend fun upsert(link: CanonicalMediaLinkEntity)

    @Upsert
    suspend fun upsertAll(links: List<CanonicalMediaLinkEntity>)

    /**
     * Purge des associations dont le média n'existe plus dans le catalogue
     * local (`CatalogReconciler`, T20 §4.5) : borne la fenêtre pendant
     * laquelle un `providerId` réattribué par le panel Xtream à une autre
     * œuvre pourrait hériter à tort d'un ancien `canonicalId`.
     */
    @Query(
        "DELETE FROM canonical_media_links WHERE NOT (" +
            "(kind = 'movie' AND providerId IN (SELECT streamId FROM vod_streams)) OR " +
            "(kind = 'series' AND providerId IN (SELECT seriesId FROM series_streams)))"
    )
    suspend fun purgeOrphaned()
}
