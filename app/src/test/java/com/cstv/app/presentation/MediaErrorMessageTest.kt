package com.cstv.app.presentation

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class MediaErrorMessageTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)


    private fun httpError(code: Int) = HttpException(
        Response.error<Any>(code, "".toResponseBody("text/plain".toMediaType()))
    )

    @Test
    fun quotaCodesExplainTheWaitInsteadOfShowingTheStatus() {
        listOf(403, 429).forEach { code ->
            val message = mediaLoadErrorMessage(httpError(code), "repli")
            assertTrue(message, message.contains("Réessaie dans quelques secondes"))
        }
    }

    @Test
    fun expiredSessionAsksForReconnection() {
        assertTrue(mediaLoadErrorMessage(httpError(401), "repli").contains("Session expirée"))
    }

    @Test
    fun serverErrorsBlameTheProvider() {
        assertTrue(mediaLoadErrorMessage(httpError(503), "repli").contains("indisponible"))
    }

    @Test
    fun otherFailuresKeepTheirOwnMessage() {
        assertEquals("timeout", mediaLoadErrorMessage(IOException("timeout"), "repli"))
    }

    @Test
    fun messagelessFailuresUseTheFallback() {
        assertEquals("repli", mediaLoadErrorMessage(IOException(), "repli"))
    }
}
