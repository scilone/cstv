package com.cstv.app.domain.sync

/**
 * Identifiants stables des sections de synchronisation du catalogue.
 *
 * Ils font partie du contrat exposé par [SyncState] : les producteurs (data)
 * et les consommateurs (presentation) doivent donc dépendre de cette couche
 * commune plutôt que de recopier leurs valeurs de stockage.
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
