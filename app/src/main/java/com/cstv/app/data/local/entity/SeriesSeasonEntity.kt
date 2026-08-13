package com.cstv.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Saison persistée lors de l'ouverture en ligne d'une fiche série. Peuplée à la
 * consultation et non par balayage : un `get_series_info` par série produirait
 * exactement le trafic que T4 supprime.
 *
 * T20 : clé étrangère vers `series_streams(seriesId)` en cascade — une série retirée du bouquet
 * ne doit plus laisser ses saisons orphelines en base indéfiniment. Le remplacement du catalogue
 * passe donc en upsert différentiel (§4.3) : un `INSERT OR REPLACE` déclencherait cette cascade à
 * chaque synchronisation.
 */
@Entity(
    tableName = "series_seasons",
    primaryKeys = ["seriesId", "seasonNumber"],
    foreignKeys = [
        ForeignKey(entity = SeriesStreamEntity::class, parentColumns = ["seriesId"], childColumns = ["seriesId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("seriesId")],
)
data class SeriesSeasonEntity(
    val seriesId: Int,
    val seasonNumber: Int,
    val name: String,
    val episodeCount: Int = 0,
    val cover: String? = null,
    val cachedAt: Long
)
