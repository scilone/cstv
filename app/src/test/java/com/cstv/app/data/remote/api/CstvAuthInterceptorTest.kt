package com.cstv.app.data.remote.api

import com.cstv.app.data.local.storage.CstvSessionManager
import com.cstv.app.domain.model.CstvSession
import com.cstv.app.domain.util.TimeProvider
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * `CstvAuthInterceptor` échoue vite plutôt que d'émettre une requête anonyme
 * (F33 §4.5) : une session absente ou localement expirée ne doit jamais
 * atteindre le réseau pour un endpoint authentifié.
 */
class CstvAuthInterceptorTest {
    @Mock private lateinit var sessions: CstvSessionManager
    @Mock private lateinit var clock: TimeProvider
    @Mock private lateinit var chain: Interceptor.Chain

    private lateinit var interceptor: CstvAuthInterceptor

    private fun requestTo(path: String) = Request.Builder().url("https://cstv.test$path").build()
    private fun fakeResponse(request: Request) = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(200).message("").build()

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        interceptor = CstvAuthInterceptor(sessions, clock)
    }

    @Test(expected = CstvSessionExpiredException::class)
    fun `no session throws instead of sending an anonymous authenticated request`() {
        val request = requestTo("/v1/me")
        whenever(chain.request()).thenReturn(request)
        whenever(sessions.get()).thenReturn(null)

        interceptor.intercept(chain)
    }

    @Test(expected = CstvSessionExpiredException::class)
    fun `locally expired session throws before any network call`() {
        val request = requestTo("/v1/me")
        whenever(chain.request()).thenReturn(request)
        whenever(sessions.get()).thenReturn(CstvSession("token", "acc", "mail@example.test", 1_000L))
        doReturn(1_000L).whenever(clock).nowMillis()

        interceptor.intercept(chain)
    }

    @Test
    fun `valid session injects the bearer header on an authenticated endpoint`() {
        val request = requestTo("/v1/me")
        whenever(chain.request()).thenReturn(request)
        whenever(sessions.get()).thenReturn(CstvSession("secret-token", "acc", "mail@example.test", 2_000L))
        doReturn(1_000L).whenever(clock).nowMillis()
        val captor = argumentCaptor<Request>()
        whenever(chain.proceed(captor.capture())).thenAnswer { fakeResponse(captor.firstValue) }

        interceptor.intercept(chain)

        assertEquals("Bearer secret-token", captor.firstValue.header("Authorization"))
    }

    @Test
    fun `otp endpoints are never authenticated, even without a session`() {
        val request = requestTo("/v1/auth/otp/request")
        whenever(chain.request()).thenReturn(request)
        whenever(sessions.get()).thenReturn(null)
        whenever(chain.proceed(request)).thenReturn(fakeResponse(request))

        interceptor.intercept(chain)

        verify(chain).proceed(request)
    }
}
