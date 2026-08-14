package com.cstv.app.domain.model

/**
 * Résultat d'une tentative de connexion automatique.
 *
 * Typé plutôt qu'un simple `UserInfo` ou une exception : sans réseau,
 * l'application doit distinguer une panne (repli hors ligne légitime) d'un
 * refus du serveur (aucun contournement possible).
 */
sealed interface AutoLoginOutcome {

    /** Aucun identifiant enregistré sur l'appareil. */
    object NoCredentials : AutoLoginOutcome

    /** Validation réseau réussie. */
    data class Online(val userInfo: UserInfo) : AutoLoginOutcome

    /**
     * Session accordée sans réseau : prolonge une validation déjà obtenue, elle
     * ne contourne aucune vérification serveur.
     */
    data class OfflineSession(val userInfo: UserInfo) : AutoLoginOutcome

    data class Rejected(val reason: AutoLoginRejection, val message: String) : AutoLoginOutcome
}

enum class AutoLoginRejection {
    /** Le panel a explicitement refusé les identifiants. */
    INVALID_CREDENTIALS,

    /** Compte expiré ou inactif : une réponse du serveur, pas une panne. */
    ACCOUNT_EXPIRED,

    /**
     * Hors ligne sans validation réseau antérieure ou sans catalogue complet
     * pour ce compte : la première connexion exige Internet.
     */
    NO_LOCAL_SESSION,

    CLOUD_CREDENTIALS_INVALID,

    CLOUD_RESTORE_UNAVAILABLE,

    CLOUD_RESTORE_FAILED,

    UNKNOWN
}
