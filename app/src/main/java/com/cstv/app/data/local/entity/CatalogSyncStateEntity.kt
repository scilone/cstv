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
