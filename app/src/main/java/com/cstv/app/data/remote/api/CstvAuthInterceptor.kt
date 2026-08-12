package com.cstv.app.data.remote.api

import com.cstv.app.data.local.storage.CstvSessionManager
import com.cstv.app.domain.util.TimeProvider
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import java.io.IOException

class CstvAuthInterceptor @Inject constructor(private val sessionManager: CstvSessionManager, private val clock: TimeProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val session = sessionManager.get()
        val requiresAuth = !chain.request().url.encodedPath.startsWith("/v1/auth/")
        if (requiresAuth && (session == null || session.isExpired(clock.nowMillis()))) {
            throw CstvSessionExpiredException()
        }
        val request = if (session != null) chain.request().newBuilder().header("Authorization", "Bearer ${session.token}").build() else chain.request()
        return chain.proceed(request)
    }
}

class CstvSessionExpiredException : IOException("CSTV session expired locally")
