package com.cstv.app.data.remote.api

import com.cstv.app.data.local.storage.CstvSessionManager
import com.cstv.app.data.remote.CstvSessionStateHolder
import com.cstv.app.data.remote.CstvErrorMapper
import com.cstv.app.domain.model.*
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class CstvSessionGuardInterceptor @Inject constructor(private val sessions: CstvSessionManager, private val holder: CstvSessionStateHolder, private val errors: CstvErrorMapper) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        when (response.code) {
            401 -> { sessions.clearSession(); holder.publish(CstvSessionState.SignedOut(SignedOutReason.SessionRejected)) }
            403 -> {
                val error = errors.fromBody(403, response.peekBody(8_192).string())
                holder.publish(
                    CstvSessionState.Blocked(
                        if (error == CstvError.Expired) CstvBlockedReason.Expired else CstvBlockedReason.Disabled
                    )
                )
            }
        }
        return response
    }
}
