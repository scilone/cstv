package com.cstv.app.domain.model

/** F45 §7.11 : tous les indices sont optionnels — les métadonnées IPTV sont souvent incomplètes ou fausses. */
data class ExternalMatchHints(
    val director: String? = null,
    val actors: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val runtimeMinutes: Int? = null,
    val youtubeTrailerKey: String? = null,
)

/** Internal queue input. Only kind, title and year leave the device for an F45 match. */
data class ExternalMetadataMatchRequest(
    val kind: String,
    val providerId: Int,
    val title: String,
    val year: Int?,
    val linkKey: String?,
    val hints: ExternalMatchHints = ExternalMatchHints(),
    val allowRefresh: Boolean = false,
)

/**
 * Résultat provider-neutral renvoyé par CSTV. Les IDs externes sont des chaînes UUID opaques.
 *
 * F45-R4/R5 : `confidence`/`matchMethod`/`matchVersion`/`ageRating` sont désormais renseignés même
 * sur un hit local (lien déjà résolu, lu depuis Room) — avant, seul un vrai appel réseau les
 * remplissait, ce qui faisait redevenir `UNCLASSIFIED` un média pourtant déjà classifié. `confidence
 * != null` ne permet donc plus de distinguer un hit local d'un match réseau frais : `fromNetwork`
 * porte cette distinction explicitement (le worker d'hydratation s'en sert pour ne propager
 * `linkKey` aux variantes, §8.10, qu'après un vrai travail réseau, jamais après un simple hit local).
 */
data class ExternalMetadataMatch(
    val externalId: String,
    val kind: String,
    val confidence: Int?,
    val matchMethod: String?,
    val matchVersion: Int?,
    val ageRating: Int?,
    val fromNetwork: Boolean = true,
)

/**
 * T29 §8.9 : résultat provider-neutral d'une tentative de matching, remplaçant l'ancien
 * `ExternalMetadataMatch?` — un simple `null` ne distinguait pas un résultat métier terminé
 * (`not_found`/`unresolved` backend) d'une impossibilité **technique** temporaire (429 fournisseur,
 * budget TMDB local épuisé, erreur réseau transitoire, §7.3). Un `Retry` ne doit jamais être persisté
 * comme un `Unresolved` (§7.3, §8.10) : l'appelant reprogramme l'item au lieu d'écrire une tentative.
 */
sealed interface ExternalMetadataMatchOutcome {
    data class Matched(val match: ExternalMetadataMatch) : ExternalMetadataMatchOutcome
    /** Résolution métier terminée sans match retenu (`not_found`/`unresolved` côté backend). */
    data object Unresolved : ExternalMetadataMatchOutcome
    /** §7.7 : `retryAfterMillis` vient du `Retry-After` backend quand connu, sinon `null` (le backoff F45 s'applique). */
    data class Retry(val retryAfterMillis: Long?) : ExternalMetadataMatchOutcome
}

/** Accès pratique au match résolu, `null` pour `Unresolved`/`Retry` — mêmes usages que l'ancien `?.` sur `ExternalMetadataMatch?`. */
val ExternalMetadataMatchOutcome.matchOrNull: ExternalMetadataMatch?
    get() = (this as? ExternalMetadataMatchOutcome.Matched)?.match
