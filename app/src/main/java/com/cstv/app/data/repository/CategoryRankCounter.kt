package com.cstv.app.data.repository

/**
 * Attribue à chaque flux son rang au sein de sa catégorie, dans l'ordre où
 * l'API Xtream les renvoie.
 *
 * `orderIndex` numérote la réponse complète : en mode « Tout », un film de la
 * dernière catégorie porte un index de l'ordre de 38 000, ce qui ne dit rien de
 * sa position dans sa propre rangée. Le rang par catégorie, lui, permet à la
 * requête de l'onglet « Tout » de s'arrêter au centième élément de chaque
 * catégorie sans rien trier (voir `VodDao.observeAllStreamListRows`).
 *
 * Volontairement non thread-safe : l'appelant l'utilise dans un unique
 * `mapIndexedNotNull` séquentiel, sur la réponse déjà ordonnée.
 */
class CategoryRankCounter {
    private val nextRank = HashMap<String, Int>()

    /** Rang du prochain flux de [categoryId] ; 0 pour le premier. */
    fun next(categoryId: String): Int {
        val rank = nextRank[categoryId] ?: 0
        nextRank[categoryId] = rank + 1
        return rank
    }
}
