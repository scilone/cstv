package com.cstv.app.data.local.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.PrimaryKey

// L'index sur releaseYear sert l'appariement TMDB : la requête ne remonte que
// les films de l'année cherchée plus ceux dont l'année n'est pas encore connue,
// au lieu de charger et normaliser tout le catalogue (T-B15).
//
// L'index sur `categoryRank` est **couvrant** : il porte les colonnes de
// `VodStreamListRow`, si bien que la requête de l'onglet « Tout » se résout
// entièrement dans l'index — aucun accès à la table, aucun tri temporaire, et
// le balayage s'arrête au centième élément de chaque catégorie. Sans lui,
// `ORDER BY orderIndex` sur 39 000 lignes imposait un parcours complet de la
// table (colonnes lourdes `plot`/`searchText` comprises) suivi d'un tri
// B-tree : 2 secondes mesurées sur Android TV.
//
// F39 (migration 31→32) : `languageTag`/`qualityTag` ajoutés en queue pour les
// badges de version — l'index T9 est étendu une seule fois, comme prévu par la
// fiche F39 §8.4, plutôt que de perdre la couverture sur l'onglet « Tout ».
//
// L'index `(categoryId, orderIndex)` reste volontairement étroit : la vue
// « une catégorie » ne remonte que quelques centaines de lignes, pour
// lesquelles les accès table restent moins coûteux que de dupliquer `name` et
// `streamIcon` une seconde fois sur le disque.
@Entity(
    tableName = "vod_streams",
    indices = [
        Index(value = ["releaseYear"]),
        Index(
            value = [
                "categoryRank", "streamId", "name", "streamIcon",
                "rating", "added", "categoryId", "genre", "releaseYear",
                "languageTag", "qualityTag"
            ]
        ),
        Index(value = ["categoryId", "orderIndex"]),
        Index(value = ["linkKey"])
    ]
)
data class VodStreamEntity(
    @PrimaryKey val streamId: Int,
    val name: String,
    val streamIcon: String?,
    val rating: String?,
    val added: String?,
    val categoryId: String,
    val cachedAt: Long,
    val actors: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val orderIndex: Int = 0,
    /**
     * Rang du film **au sein de sa catégorie** (0 = premier), là où
     * `orderIndex` est un rang global sur toute la réponse de l'API. C'est ce
     * rang qui permet à l'onglet « Tout » de ne demander que les 100 premiers
     * films de chaque catégorie en SQL — les rangées horizontales n'en
     * affichent pas davantage, le total réel venant de `categoryCounts`.
     */
    @ColumnInfo(defaultValue = "0") val categoryRank: Int = 0,
    val releaseYear: Int? = null,
    /** Métadonnées de get_vod_info conservées pour la fiche hors ligne. */
    val plot: String? = null,
    val duration: String? = null,
    val containerExtension: String? = null,
    val detailsCachedAt: Long? = null,
    @ColumnInfo(defaultValue = "''") val searchText: String = "",
    @ColumnInfo(defaultValue = "''") val cleanTitle: String = "",
    @ColumnInfo(defaultValue = "''") val linkKey: String = "",
    val languageTag: String? = null,
    val languageRaw: String? = null,
    val qualityTag: String? = null,
    val qualityRaw: String? = null
)
