package com.cstv.app.presentation

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class MediaErrorMessageTest {

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
