package com.cstv.app.data.local.dao

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
    val releaseYear: Int?
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
    val releaseYear: Int?
)
