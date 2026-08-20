package com.cstv.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * Association `externalId` (backend CSTV, opaque — jamais parsé) <-> média
 * local (`kind` "movie"|"series", `providerId` = streamId/seriesId), une fois
 * résolue par [com.cstv.app.domain.model.ExternalCatalogMatcher] (T24).
 *
 * Table volontairement découplée de `vod_streams`/`series_streams` : ces
 * tables sont réécrites en intégralité à chaque sync catalogue via `@Upsert`
 * (T20-R4), qui régénère toutes leurs colonnes depuis Xtream — un
 * `externalId` stocké dessus serait donc écrasé à `null` à la resync
 * suivante, contrairement à `linkKey` (T21) qui est recalculable depuis le
 * titre. Cette table n'est jamais touchée par la sync catalogue, purgée
 * uniquement de ses lignes orphelines par `CatalogReconciler` (T20 §4.5).
 *
 * `externalId` n'est pas la clé primaire : plusieurs `providerId` (versions
 * multiples d'une même œuvre) peuvent partager le même `externalId`.
 */
@Entity(
    tableName = "external_catalog_links",
    primaryKeys = ["kind", "providerId"],
    indices = [Index(value = ["externalId"])]
)
data class ExternalCatalogLinkEntity(
    val kind: String,
    val providerId: Int,
    val externalId: String,
    val updatedAt: Long
)
