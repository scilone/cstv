package com.cstv.app.data.local.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.PrimaryKey

// Voir VodStreamEntity : index dédié à l'appariement TMDB par année, index
// couvrant sur `categoryRank` pour l'onglet « Tout », index étroit
// `(categoryId, orderIndex)` pour la vue d'une seule catégorie.
@Entity(
    tableName = "series_streams",
    indices = [
        Index(value = ["releaseYear"]),
        Index(
            value = [
                "categoryRank", "seriesId", "name", "cover",
                "rating", "added", "categoryId", "genre", "releaseYear"
            ]
        ),
        Index(value = ["categoryId", "orderIndex"]),
        Index(value = ["linkKey"])
    ]
)
data class SeriesStreamEntity(
    @PrimaryKey val seriesId: Int,
    val name: String,
    val cover: String?,
    val rating: String?,
    val added: String?,
    val categoryId: String,
    val cachedAt: Long,
    val actors: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val orderIndex: Int = 0,
    /** Voir [VodStreamEntity.categoryRank] : rang au sein de la catégorie. */
    @ColumnInfo(defaultValue = "0") val categoryRank: Int = 0,
    val releaseYear: Int? = null,
    /** Métadonnées de get_series_info conservées pour la fiche hors ligne. */
    val plot: String? = null,
    val detailsCachedAt: Long? = null,
    @ColumnInfo(defaultValue = "''") val searchText: String = "",
    @ColumnInfo(defaultValue = "''") val cleanTitle: String = "",
    @ColumnInfo(defaultValue = "''") val linkKey: String = "",
    val languageTag: String? = null,
    val languageRaw: String? = null,
    val qualityTag: String? = null,
    val qualityRaw: String? = null
)
