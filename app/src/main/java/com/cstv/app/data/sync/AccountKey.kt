package com.cstv.app.data.sync

import com.cstv.app.domain.model.Credentials
import java.security.MessageDigest

/**
 * Identité du compte qui a validé une session hors ligne.
 *
 * SHA-256 tronqué de `host:port:username`. Le mot de passe n'entre jamais dans
 * le hash : un simple changement de mot de passe ne purge donc pas le
 * catalogue, ce qui est le comportement voulu — c'est le même abonné.
 */
object AccountKey {

    private const val KEY_LENGTH = 32

    fun from(credentials: Credentials): String =
        from(credentials.host, credentials.port, credentials.username)

    fun from(host: String, port: Int, username: String): String {
        val raw = "${host.trim().lowercase()}:$port:${username.trim()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(KEY_LENGTH)
    }
}

/**
 * Identité du serveur propriétaire du catalogue partagé.
 *
 * Le catalogue est conservé quand seul l'utilisateur Xtream change sur le
 * même `host:port`; l'autorisation de session hors ligne reste, elle, liée à
 * [AccountKey].
 */
object CatalogServerKey {

    private const val KEY_LENGTH = 32

    fun from(credentials: Credentials): String = from(credentials.host, credentials.port)

    fun from(host: String, port: Int): String {
        val raw = "${host.trim().lowercase()}:$port"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(KEY_LENGTH)
    }
}
