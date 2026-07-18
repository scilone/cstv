package com.cstv.app.data.remote.api

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class DynamicBaseUrlInterceptor : Interceptor {
    @Volatile
    var hostUrl: String? = null

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val currentHost = hostUrl
        if (currentHost != null) {
            try {
                val cleanHost = getHostName(currentHost)
                val cleanPort = getHostPort(currentHost)
                val scheme = if (currentHost.startsWith("https")) "https" else "http"
                
                val newUrl = request.url.newBuilder()
                    .scheme(scheme)
                    .host(cleanHost)
                    .port(cleanPort)
                    .build()
                
                request = request.newBuilder()
                    .url(newUrl)
                    .build()
            } catch (e: Exception) {
                // Fallback to original request if parsing fails
            }
        }
        return chain.proceed(request)
    }

    private fun getHostName(url: String): String {
        var clean = url.replace("http://", "").replace("https://", "")
        if (clean.contains(":")) {
            clean = clean.substringBefore(":")
        }
        if (clean.contains("/")) {
            clean = clean.substringBefore("/")
        }
        return clean
    }

    private fun getHostPort(url: String): Int {
        var clean = url.replace("http://", "").replace("https://", "")
        if (clean.contains("/")) {
            clean = clean.substringBefore("/")
        }
        if (clean.contains(":")) {
            val portStr = clean.substringAfter(":")
            return portStr.toIntOrNull() ?: if (url.startsWith("https")) 443 else 80
        }
        return if (url.startsWith("https")) 443 else 80
    }
}
