package com.cstv.app.data.sync

import com.cstv.app.domain.model.Credentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

class AccountKeyTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)


    private fun credentials(
        host: String = "panel.example.com",
        port: Int = 8080,
        username: String = "user",
        password: String = "secret"
    ) = Credentials(host, port, username, password, rememberMe = true)

    /**
     * Une session hors ligne reste liée à un utilisateur, pas à son mot de passe.
     */
    @Test
    fun keyIsStableWhenOnlyPasswordChanges() {
        assertEquals(
            AccountKey.from(credentials(password = "secret")),
            AccountKey.from(credentials(password = "rotated-2026"))
        )
    }

    @Test
    fun keyChangesWithHostPortOrUsername() {
        val reference = AccountKey.from(credentials())
        assertNotEquals(reference, AccountKey.from(credentials(host = "other.example.com")))
        assertNotEquals(reference, AccountKey.from(credentials(port = 8081)))
        assertNotEquals(reference, AccountKey.from(credentials(username = "someone-else")))
    }

    @Test
    fun keyIgnoresHostCaseAndSurroundingWhitespace() {
        assertEquals(
            AccountKey.from(credentials(host = "panel.example.com")),
            AccountKey.from(credentials(host = "  PANEL.Example.COM  "))
        )
    }

    /** Le mot de passe ne doit apparaître ni en clair ni sous forme dérivée. */
    @Test
    fun keyNeverContainsCredentialMaterial() {
        val key = AccountKey.from(credentials())
        assertFalse(key.contains("secret"))
        assertFalse(key.contains("user"))
        assertEquals(32, key.length)
    }

    @Test
    fun catalogServerKeyIsSharedByUsersOnTheSameServer() {
        val reference = CatalogServerKey.from(credentials(username = "first-user"))

        assertEquals(reference, CatalogServerKey.from(credentials(username = "second-user")))
        assertNotEquals(reference, CatalogServerKey.from(credentials(host = "other.example.com")))
        assertNotEquals(reference, CatalogServerKey.from(credentials(port = 8081)))
    }
}
