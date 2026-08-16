package com.cstv.app.data.local.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.PrimaryKey

// Voir VodStreamEntity : index couvrant sur `categoryRank` pour l'onglet
// « Tout », index étroit `(categoryId, num)` pour la vue d'une seule catégorie.
// Les chaînes se trient par `num` (numéro de chaîne fourni par le panel) et non
// par l'ordre de la réponse — c'est donc `num` qui définit le rang.
@Entity(
    tableName = "live_streams",
    indices = [
        Index(
            value = [
                "categoryRank", "streamId", "name", "streamIcon",
                "epgChannelId", "num", "categoryId"
            ]
        ),
        Index(value = ["categoryId", "num"]),
        Index(value = ["linkKey"])
    ]
)
data class LiveStreamEntity(
    @PrimaryKey val streamId: Int,
    val name: String,
    val streamIcon: String?,
    val epgChannelId: String?,
    val num: Int,
    val categoryId: String,
    val cachedAt: Long,
    /** Voir [VodStreamEntity.categoryRank] : rang au sein de la catégorie. */
    @ColumnInfo(defaultValue = "0") val categoryRank: Int = 0,
    @ColumnInfo(defaultValue = "''") val searchText: String = "",
    @ColumnInfo(defaultValue = "''") val cleanTitle: String = "",
    @ColumnInfo(defaultValue = "''") val linkKey: String = "",
    val languageTag: String? = null,
    val languageRaw: String? = null,
    val qualityTag: String? = null,
    val qualityRaw: String? = null
)
