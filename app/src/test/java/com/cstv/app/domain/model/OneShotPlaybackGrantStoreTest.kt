package com.cstv.app.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OneShotPlaybackGrantStoreTest {

    private val store = OneShotPlaybackGrantStore()

    @Test
    fun `an issued grant can be consumed exactly once`() {
        val nonce = store.issue(profileId = 1, mediaUid = "movie:42")

        assertTrue(store.consume(1, "movie:42", nonce))
        assertFalse(store.consume(1, "movie:42", nonce)) // relecture du même média refusée
    }

    @Test
    fun `a grant does not authorize a different media`() {
        val nonce = store.issue(profileId = 1, mediaUid = "movie:42")

        assertFalse(store.consume(1, "movie:43", nonce))
        // Le grant original reste consommable : la tentative sur un autre média ne l'a pas brûlé.
        assertTrue(store.consume(1, "movie:42", nonce))
    }

    @Test
    fun `a grant does not authorize a different profile`() {
        val nonce = store.issue(profileId = 1, mediaUid = "movie:42")

        assertFalse(store.consume(2, "movie:42", nonce))
    }

    @Test
    fun `a grant issued for playback never matches a profile-settings action`() {
        val nonce = store.issue(profileId = 1, mediaUid = "movie:42")

        // La modification du niveau autorisé utilise un identifiant distinct des
        // médias : aucune collision possible avec un grant de lecture (§8.4).
        assertFalse(store.consume(1, "profile-settings:1", nonce))
    }

    @Test
    fun `an unknown nonce is never accepted`() {
        assertFalse(store.consume(1, "movie:42", "unknown-nonce"))
    }

    @Test
    fun `oldest abandoned grants are evicted when the bounded store is full`() {
        val firstNonce = store.issue(profileId = 1, mediaUid = "movie:0")
        repeat(255) { index ->
            store.issue(profileId = 1, mediaUid = "movie:${index + 1}")
        }
        val lastNonce = store.issue(profileId = 1, mediaUid = "movie:256")

        assertFalse(store.consume(1, "movie:0", firstNonce))
        assertTrue(store.consume(1, "movie:256", lastNonce))
    }
}
