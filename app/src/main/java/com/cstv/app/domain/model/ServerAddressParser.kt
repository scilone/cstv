package com.cstv.app.domain.model

/**
 * Parsing défensif du champ unique "adresse du serveur" (Phase 28) en
 * host/port avant l'appel à player_api.php. Reste conforme à la logique
 * réseau existante : `Credentials.host` peut porter un préfixe de scheme,
 * `Credentials.port` est numérique séparé — voir `Credentials.baseUrl`.
 */
object ServerAddressParser {

    sealed class Result {
        data class Success(val host: String, val port: Int) : Result()
        data class Error(val message: String) : Result()
    }

    fun parse(input: String): Result {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            return Result.Error("Adresse du serveur requise.")
        }

        val hasHttps = trimmed.startsWith("https://", ignoreCase = true)
        val hasScheme = hasHttps || trimmed.startsWith("http://", ignoreCase = true)
        val scheme = if (hasHttps) "https://" else "http://"
        val withoutScheme = if (hasScheme) trimmed.substringAfter("://") else trimmed

        // Ignore un éventuel chemin après le port (ex: mondns.com:8080/xtream)
        val hostAndPort = withoutScheme.substringBefore("/").trim()

        if (!hostAndPort.contains(":")) {
            return Result.Error("Port manquant. Format attendu : hôte:port (ex: mondns.com:8080).")
        }

        val hostPart = hostAndPort.substringBeforeLast(":").trim()
        val portPart = hostAndPort.substringAfterLast(":").trim()

        if (hostPart.isBlank()) {
            return Result.Error("Nom d'hôte manquant.")
        }

        val port = portPart.toIntOrNull()
        if (port == null || port <= 0 || port > 65535) {
            return Result.Error("Port invalide : \"$portPart\" n'est pas un numéro de port valide.")
        }

        return Result.Success(host = "$scheme$hostPart", port = port)
    }

    /**
     * Reconstitue l'adresse complète affichable (scheme + host + port) à
     * partir d'identifiants déjà stockés (host/port séparés, Phase 1), pour
     * préremplir le champ fusionné sans forcer une nouvelle saisie.
     */
    fun buildDisplayAddress(host: String, port: Int): String {
        if (host.isBlank()) return ""
        val hasScheme = host.startsWith("http://", ignoreCase = true) ||
            host.startsWith("https://", ignoreCase = true)
        val prefixed = if (hasScheme) host else "http://$host"
        return if (port > 0) "$prefixed:$port" else prefixed
    }
}
