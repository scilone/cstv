package com.cstv.app.domain.model

class InvalidCredentialsException(message: String) : Exception(message)
class AccountExpiredException(message: String, val expiryDate: String) : Exception(message)
class ServerUnreachableException(message: String, cause: Throwable) : Exception(message, cause)
class NetworkTimeoutException(message: String, cause: Throwable) : Exception(message, cause)
class UnknownAuthException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * T29 débit : le backend CSTV a refusé la requête pour **cadence** (HTTP 429 throttle catalogue),
 * pas pour une panne. Distinct de toute autre erreur réseau : les items concernés doivent rester en
 * file et être reprogrammés sur le `Retry-After` du serveur, jamais sur le backoff exponentiel
 * 10 → 360 min — c'est ce backoff qui faisait tomber le débit réel du backfill à ~25 médias/min.
 *
 * `retryAfterMillis` est `null` quand le serveur n'a pas fourni d'en-tête exploitable ; l'appelant
 * applique alors un délai de cadence court, toujours pas le backoff d'échec.
 */
class CatalogThrottledException(val retryAfterMillis: Long?) : Exception("Catalog matching throttled by the CSTV backend")
