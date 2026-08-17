package com.cstv.app.domain.model

/**
 * F39 : construit au maximum deux étiquettes (langue puis qualité) à partir
 * des tags T21 déjà persistés (`languageTag`, `qualityTag`). Jamais de badge
 * fabriqué : un tag absent ou non reconnu ne produit simplement aucun badge
 * pour sa position (décision produit étape 2, §7.3 de la fiche F39).
 *
 * Le libellé brut Xtream (`languageRaw`/`qualityRaw`) n'est jamais utilisé
 * ici pour la V1 (§8.1) : ce mapper est la seule source de vérité pour
 * l'affichage, centralisée pour ne pas diverger entre les listes.
 */
fun mediaVersionBadges(languageTag: String?, qualityTag: String?): List<String> {
    val badges = mutableListOf<String>()
    MediaLanguage.entries.firstOrNull { it.storageCode == languageTag }?.let { badges += it.name }
    qualityDisplayLabel(qualityTag)?.let { badges += it }
    return badges
}

/**
 * F39, correction F39-R7 : libellé d'une version dans un sélecteur (lecteur
 * ou fiche média), centralisé pour ne jamais diverger entre les quatre
 * surfaces (`VodPlayerScreen`, `SeriesPlayerScreen`, `VodDetailsScreen`,
 * `SeriesDetailsScreen`). Rejoint [mediaVersionBadges] avec « · » ; si tous
 * les attributs sont absents, retombe sur [fallback] — jamais sur le
 * libellé Xtream brut (décision étape 2), même dans ce cas limite (décision
 * étape 7 : libellé fixe et localisé, fourni par l'appelant pour rester une
 * fonction pure sans dépendance Android).
 */
fun mediaVersionSelectorLabel(languageTag: String?, qualityTag: String?, fallback: String): String =
    mediaVersionBadges(languageTag, qualityTag).takeIf { it.isNotEmpty() }?.joinToString(" · ") ?: fallback

/** Libellé lisible pour un [MediaQuality.storageCode] ; `UHD_4K` s'affiche « 4K », pas son nom d'enum. */
private fun qualityDisplayLabel(qualityTag: String?): String? =
    when (MediaQuality.entries.firstOrNull { it.storageCode == qualityTag }) {
        MediaQuality.SD -> "SD"
        MediaQuality.HD -> "HD"
        MediaQuality.FHD -> "FHD"
        MediaQuality.UHD_4K -> "4K"
        null -> null
    }
