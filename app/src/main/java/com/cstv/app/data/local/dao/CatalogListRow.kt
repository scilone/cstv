package com.cstv.app.data.local.dao

/**
 * Nombre de médias remontés par catégorie sur l'onglet « Tout ».
 *
 * Les rangées horizontales de cet onglet ne sont pas conçues pour être
 * parcourues au-delà de quelques dizaines d'éléments — elles proposent
 * « Voir tout » pour la suite, et le compteur affiché vient de
 * `getCategoryCounts`, pas de la longueur de la liste. Charger les 39 000
 * films du catalogue pour n'en afficher qu'une centaine par rangée coûtait
 * autant en curseur SQLite qu'en objets vivants sur le tas (heap de 192 Mo sur
 * Android TV).
 *
 * À garder aligné sur les `CATEGORY_ROW_MAX_ITEMS` de `VodScreen`,
 * `SeriesScreen` et `LiveTvComponents`, qui décident à partir de quel rang la
 * carte « Voir tout » remplace les vignettes. Une valeur plus basse ici
 * couperait des rangées sans que la carte n'apparaisse.
 */
const val ALL_MODE_ROWS_PER_CATEGORY = 100

/**
 * Projection minimale d'un flux pour les listes de catalogue.
 *
 * `SELECT *` sur `vod_streams` ramenait aussi `plot`, `searchText`, `actors`,
 * `director`, `duration`, `containerExtension` et les horodatages de cache —
 * aucun n'étant lu par les écrans de liste. Sur l'onglet « Tout », soit près de
 * 39 000 lignes, ces colonnes gonflaient chaque enregistrement bien au-delà de
 * ce que la fenêtre de curseur d'Android (2 Mo) peut contenir : le curseur
 * devait être rerempli des dizaines de fois, et le tri temporaire de
 * `ORDER BY orderIndex` déplaçait autant d'octets inutiles. Le même écueil est
 * déjà documenté sur `getStreamsByReleaseYearPage`.
 *
 * La projection seule ne suffisait pas : le tri restait, et le curseur portait
 * toujours les 39 000 lignes. D'où les deux compléments — le plafond
 * [ALL_MODE_ROWS_PER_CATEGORY] et l'index couvrant déclaré sur
 * `VodStreamEntity`, qui font de la requête « Tout » un simple balayage
 * d'index tronqué.
 *
 * Les champs absents du domaine sont donc laissés à leur valeur neutre par les
 * repositories : `actors`, `director` et `searchText` ne sont lus que par la
 * recherche avancée et les fiches détaillées, qui passent par leurs propres
 * requêtes.
 */
data class VodStreamListRow(
    val streamId: Int,
    val name: String,
    val streamIcon: String?,
    val rating: String?,
    val added: String?,
    val categoryId: String,
    val genre: String?,
    val releaseYear: Int?,
    /** F39 : tags T21 (langue/qualité), toujours en base pour le tri des
     *  versions — l'index couvrant T9 est étendu en migration 31→32. */
    val languageTag: String?,
    val qualityTag: String?,
    /** F39 (évolution) : libellé combiné affiché en badge (§8.4) — index
     *  couvrant réétendu en migration 32→33. */
    val versionLabel: String?
)

/**
 * Voir [VodStreamListRow]. La table `live_streams` est nettement plus étroite,
 * mais la projection reste nécessaire : c'est elle que porte l'index couvrant,
 * et `searchText` n'y aurait rien à faire.
 *
 * Ces six colonnes sont exactement celles de `LiveStream` : rien n'est laissé à
 * une valeur neutre côté domaine, contrairement aux catalogues VOD et séries.
 */
data class LiveStreamListRow(
    val streamId: Int,
    val name: String,
    val streamIcon: String?,
    val epgChannelId: String?,
    val num: Int,
    val categoryId: String
)

/** Voir [VodStreamListRow]. */
data class SeriesStreamListRow(
    val seriesId: Int,
    val name: String,
    val cover: String?,
    val rating: String?,
    val added: String?,
    val categoryId: String,
    val genre: String?,
    val releaseYear: Int?,
    /** F39 : voir [VodStreamListRow]. */
    val languageTag: String?,
    val qualityTag: String?,
    /** F39 (évolution) : voir [VodStreamListRow]. */
    val versionLabel: String?
)
