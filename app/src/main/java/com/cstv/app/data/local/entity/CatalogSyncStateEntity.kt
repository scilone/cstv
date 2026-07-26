package com.cstv.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Fraîcheur du catalogue, par section et par serveur Xtream.
 *
 * En base plutôt qu'en préférences parce que l'horodatage doit être écrit dans
 * la même transaction que les données qu'il décrit, purgé avec elles, et porter
 * l'échec autant que le succès.
 */
@Entity(tableName = "catalog_sync_state")
data class CatalogSyncStateEntity(
    @PrimaryKey val section: String,
    /** SHA-256 tronqué de "host:port" — jamais d'identifiant utilisateur. */
    val accountKey: String,
    val lastSuccessAt: Long = 0L,
    val lastAttemptAt: Long = 0L,
    val lastFailureAt: Long = 0L,
    val lastFailureKind: String? = null,
    val itemCount: Int = 0
)

/**
 * Sections suivies. Les six premières composent le catalogue : c'est sur elles
 * que se calcule la complétude qui autorise le mode hors-ligne.
 */
object CatalogSection {
    const val LIVE_CATEGORIES = "live_categories"
    const val LIVE_STREAMS = "live_streams"
    const val VOD_CATEGORIES = "vod_categories"
    const val VOD_STREAMS = "vod_streams"
    const val SERIES_CATEGORIES = "series_categories"
    const val SERIES_STREAMS = "series_streams"
    const val ENRICHMENT = "enrichment"
    const val EPG = "epg"

    val CATALOG_SECTIONS = listOf(
        LIVE_CATEGORIES,
        LIVE_STREAMS,
        VOD_CATEGORIES,
        VOD_STREAMS,
        SERIES_CATEGORIES,
        SERIES_STREAMS
    )
}
