package com.cstv.app.domain.model

/**
 * Retour utilisateur F40 du 2026-08-18 : en mode automatique, une chaîne ne doit apparaître
 * qu'une seule fois dans les listes (« Tout », catégories filtrées, accueil) — plus une vignette
 * par variante (`TF1 4K`/`TF1 FHD`/`TF1 HD`/`TF1 SD`). Le mode automatique choisit déjà la
 * meilleure qualité qui fonctionne à l'ouverture du lecteur (§8.4) : la vignette représentante
 * n'a besoin d'être qu'une entrée du groupe, celle de meilleure qualité déclarée pour l'affichage.
 *
 * Une chaîne sans `linkKey` (T21 pas encore calculé, ou vraiment seule de son groupe) garde sa
 * propre vignette, comme en mode manuel — c'est déjà le comportement documenté d'une chaîne sans
 * variante.
 */
fun List<LiveStream>.collapsedForAutomaticQuality(): List<LiveStream> {
    val bestByLinkKey = mutableMapOf<String, LiveStream>()
    val order = mutableListOf<String>()
    val ungrouped = mutableListOf<LiveStream>()
    val positions = mutableMapOf<Any, Int>()

    forEachIndexed { index, stream ->
        val linkKey = stream.linkKey
        if (linkKey.isBlank()) {
            ungrouped += stream
            positions[stream.streamId] = index
            return@forEachIndexed
        }
        val current = bestByLinkKey[linkKey]
        if (current == null) {
            bestByLinkKey[linkKey] = stream
            order += linkKey
            positions[linkKey] = index
        } else if (mediaQualityRank(stream.qualityTag) > mediaQualityRank(current.qualityTag)) {
            bestByLinkKey[linkKey] = stream
        }
    }

    val grouped: List<Any> = order + ungrouped.map { it.streamId }
    return grouped.sortedBy { positions.getValue(it) }.map { key ->
        when (key) {
            is String -> bestByLinkKey.getValue(key)
            else -> ungrouped.first { it.streamId == key }
        }
    }
}
