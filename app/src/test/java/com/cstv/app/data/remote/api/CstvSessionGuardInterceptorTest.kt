package com.cstv.app.data.remote.api

import com.cstv.app.data.local.storage.CstvSessionManager
import com.cstv.app.data.remote.CstvErrorMapper
import com.cstv.app.data.remote.CstvSessionStateHolder
import com.cstv.app.domain.model.CstvBlockedReason
import com.cstv.app.domain.model.CstvSessionState
import com.cstv.app.domain.model.SignedOutReason
import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

/**
 * Ce garde observe TOUTE réponse du client CSTV, quelle que soit la couche
 * appelante (F33 §5.3) : un `401`/`403` doit transiter l'état de session sans
 * que l'appelant ait à s'en soucier.
 */
class CstvSessionGuardInterceptorTest {
    @Mock private lateinit var sessions: CstvSessionManager
    @Mock private lateinit var chain: Interceptor.Chain

    private lateinit var holder: CstvSessionStateHolder
    private lateinit var interceptor: CstvSessionGuardInterceptor
    private val request = Request.Builder().url("https://cstv.test/v1/me").build()

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        holder = CstvSessionStateHolder()
        interceptor = CstvSessionGuardInterceptor(sessions, holder, CstvErrorMapper(Gson()))
        whenever(chain.request()).thenReturn(request)
    }

    private fun responseWith(code: Int, body: String = "") = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("")
        .body(body.toResponseBody("application/json".toMediaType()))
        .build()

    @Test fun `401 on any endpoint purges the token and signs the session out as rejected`() {
        whenever(chain.proceed(request)).thenReturn(responseWith(401))

        interceptor.intercept(chain)

        verify(sessions).clearSession()
        val state = holder.state.value
        assertTrue(state is CstvSessionState.SignedOut)
        assertEquals(SignedOutReason.SessionRejected, (state as CstvSessionState.SignedOut).reason)
    }

    @Test fun `403 ACCOUNT_DISABLED blocks the session as disabled`() {
        whenever(chain.proceed(request)).thenReturn(responseWith(403, "{\"code\":\"ACCOUNT_DISABLED\"}"))

        interceptor.intercept(chain)

        verifyNoInteractions(sessions)
        val state = holder.state.value
        assertTrue(state is CstvSessionState.Blocked)
        assertEquals(CstvBlockedReason.Disabled, (state as CstvSessionState.Blocked).reason)
    }

    @Test fun `403 ACCOUNT_EXPIRED blocks the session as expired`() {
        whenever(chain.proceed(request)).thenReturn(responseWith(403, "{\"code\":\"ACCOUNT_EXPIRED\"}"))

        interceptor.intercept(chain)

        val state = holder.state.value
        assertTrue(state is CstvSessionState.Blocked)
        assertEquals(CstvBlockedReason.Expired, (state as CstvSessionState.Blocked).reason)
    }

    @Test fun `unknown 403 fails closed as disabled`() {
        whenever(chain.proceed(request)).thenReturn(responseWith(403, "not json"))

        interceptor.intercept(chain)

        val state = holder.state.value
        assertTrue(state is CstvSessionState.Blocked)
        assertEquals(CstvBlockedReason.Disabled, (state as CstvSessionState.Blocked).reason)
    }

    @Test fun `a 2xx response never mutates the session state`() {
        whenever(chain.proceed(request)).thenReturn(responseWith(200, "{}"))

        interceptor.intercept(chain)

        verifyNoInteractions(sessions)
        assertEquals(CstvSessionState.Unknown, holder.state.value)
    }
}
