package com.cstv.app.domain.model

/**
 * T29 cycle backfill P0-1 : nature d'une reprogrammation `retry`. Modèle **explicite** plutôt
 * qu'heuristique sur la valeur de `retryAfter` — deux causes très différentes peuvent renvoyer la
 * même durée, et seule la cause décide si l'item a réellement *échoué*.
 *
 * Règle : seule une vraie impossibilité imputable au média (fournisseur en erreur, réseau) compte
 * comme une tentative et alimente le backoff exponentiel F45. Un refus de cadence ([THROTTLE]) ou
 * un item que le backend n'a pas eu le temps de commencer dans son budget de requête
 * ([BATCH_DEADLINE]) ne sont pas des échecs : l'item reste en file, reprogrammé sur le délai
 * fourni, `attemptCount` inchangé. Sans cela, ~26 items sur 50 d'un batch froid gonflaient
 * `attemptCount` à chaque passage et la première vraie erreur les condamnait d'emblée à un backoff
 * de plusieurs heures.
 */
enum class HydrationRetryReason {
    /** HTTP 429 de cadence du backend CSTV (quota compte/IP) — jamais une tentative du média. */
    THROTTLE,

    /** Le backend a manqué de budget de requête avant de commencer cet item — jamais une tentative. */
    BATCH_DEADLINE,

    /** Le fournisseur (TMDB) a refusé/échoué temporairement pour cet item — vraie tentative. */
    PROVIDER,

    /** Panne réseau, réponse illisible, ou `retry` sans raison exploitable (backend antérieur) — vraie tentative. */
    NETWORK,
    ;

    /** `true` quand l'item doit voir son `attemptCount` incrémenté et suivre le backoff F45. */
    val countsAsAttempt: Boolean get() = this == PROVIDER || this == NETWORK

    companion object {
        internal const val WIRE_BATCH_DEADLINE = "batch_deadline"
        internal const val WIRE_PROVIDER = "provider"

        /**
         * Compatibilité descendante : un backend qui n'envoie pas encore `retryReason` (ou une valeur
         * inconnue d'une version future) retombe sur [NETWORK], c'est-à-dire exactement le
         * comportement d'avant ce correctif — jamais sur une exemption de backoff non méritée.
         */
        fun fromWire(raw: String?): HydrationRetryReason = when (raw) {
            WIRE_BATCH_DEADLINE -> BATCH_DEADLINE
            WIRE_PROVIDER -> PROVIDER
            else -> NETWORK
        }
    }
}
