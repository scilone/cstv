package com.cstv.app.domain.model

/**
 * F45 §7.5 : ordre de priorité fixe de la file d'hydratation. `wireValue` est persisté tel quel
 * dans `external_hydration_queue.reason` (Room). Pas de priorité `STALE_METADATA` de fond : les
 * métadonnées expirées ne sont jamais scannées ni mises en file périodiquement (§7.1/§7.5).
 */
enum class HydrationReason(val wireValue: String, val priority: Int) {
    DETAIL_OPEN("DETAIL_OPEN", 3),
    NEW_IPTV_MEDIA("NEW_IPTV_MEDIA", 2),
    MISSING_METADATA("MISSING_METADATA", 1),
}
