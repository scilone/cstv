package com.cstv.app.domain.sync

/**
 * Nature d'un échec de synchronisation. Sert à la fois au message non bloquant
 * affiché à l'utilisateur et à la décision de poursuivre ou d'arrêter la sync.
 */
enum class SyncFailureKind {
    /** Réseau indisponible ou timeout : le catalogue local reste la vérité. */
    NETWORK,

    /** Identifiants rejetés ou compte expiré : seul cas d'arrêt immédiat. */
    AUTH,

    /** Le panel répond mais refuse ou renvoie une réponse inexploitable (403/429/5xx, liste vide). */
    PANEL,

    /** Disque plein : on conserve l'existant et on ne reboucle pas. */
    STORAGE,

    /** Réponse illisible côté JSON. */
    PARSE,

    UNKNOWN
}

/** Origine d'un déclenchement de synchronisation. */
enum class SyncTrigger {
    MANUAL,
    SCHEDULED,
    STARTUP,
    RECONNECT
}

/**
 * Résultat d'une section. Une écriture ne lève plus d'exception vers son
 * appelant : elle la classe, pour que l'échec d'une section n'annule pas les
 * sections déjà committées.
 */
sealed interface SectionSyncOutcome {
    data class Success(val itemCount: Int) : SectionSyncOutcome
    data class Failure(val kind: SyncFailureKind, val cause: Throwable? = null) : SectionSyncOutcome
}

/** Résultat global d'une demande de synchronisation. */
sealed interface SyncOutcome {
    /** Toutes les sections tentées ont réussi. */
    data class Success(val at: Long) : SyncOutcome

    /** Au moins une section a échoué ; les sections réussies restent committées. */
    data class Failure(val kind: SyncFailureKind, val at: Long) : SyncOutcome

    /** Une synchronisation était déjà en cours : aucune requête supplémentaire émise. */
    object AlreadyRunning : SyncOutcome

    /** Catalogue frais ou appareil hors ligne : rien à faire. */
    object Skipped : SyncOutcome
}

/** État observable de la synchronisation en cours. */
sealed interface SyncState {
    object Idle : SyncState
    data class Running(val section: String, val done: Int, val total: Int) : SyncState
    data class Success(val at: Long) : SyncState
    data class Failed(val kind: SyncFailureKind, val at: Long) : SyncState
}

/**
 * Statut du catalogue local exposé à l'UI.
 *
 * [isOffline] n'est pas la simple négation de la connectivité : un transport
 * actif ne garantit pas que le panel réponde. Il combine l'état réseau et le
 * dernier échec typé NETWORK.
 *
 * [isNetworkOnline] (T7-R1) est en revanche la connectivité réelle brute,
 * sans historique d'échec : un ancien échec réseau ne doit pas continuer à
 * décrire l'appareil comme déconnecté une fois la connexion revenue, alors
 * qu'une synchronisation silencieuse s'en occupe déjà (règles métier 4/5 de T7).
 * Aucun bandeau hors ligne n'est affiché sur les listes de médias : le
 * catalogue local reste servi tel quel, sans commentaire.
 */
data class CatalogStatus(
    /** Les 6 sections catalogue ont toutes au moins une synchronisation réussie. */
    val isComplete: Boolean = false,
    /** MIN(lastSuccessAt) sur les 6 sections ; 0 tant que le catalogue est incomplet. */
    val lastFullSyncAt: Long = 0L,
    val isStale: Boolean = true,
    val isOffline: Boolean = false,
    val isNetworkOnline: Boolean = true,
    val isSyncing: Boolean = false,
    val lastFailureKind: SyncFailureKind? = null
)
