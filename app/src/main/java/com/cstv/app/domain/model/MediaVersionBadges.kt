package com.cstv.app.domain.model

/**
 * F39 (évolution PO) : `versionLabel` (T21) est désormais l'unique source
 * d'affichage des badges/sélecteurs de version — tous les fragments reconnus
 * du titre (langue, qualité, technique), dans leur ordre d'apparition, joints
 * par « · » (ex. « VO · STFR · 4K »), jamais reformulés ni réduits à deux
 * catégories. `languageTag`/`qualityTag` restent en base, réservés au tri des
 * versions par qualité (`mediaQualityRank`).
 */
fun mediaVersionBadges(versionLabel: String?): List<String> =
    versionLabel?.split(" · ")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

/**
 * F39, correction F39-R7 : libellé d'une version dans un sélecteur (lecteur
 * ou fiche média) — retombe sur [fallback] si aucun fragment n'est reconnu,
 * jamais sur le libellé Xtream brut (décision étape 2), même dans ce cas
 * limite (décision étape 7 : libellé fixe et localisé, fourni par l'appelant
 * pour rester une fonction pure sans dépendance Android).
 */
fun mediaVersionSelectorLabel(versionLabel: String?, fallback: String): String =
    versionLabel?.takeIf { it.isNotBlank() } ?: fallback
