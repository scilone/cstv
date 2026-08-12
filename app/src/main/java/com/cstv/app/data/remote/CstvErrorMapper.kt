package com.cstv.app.data.remote

import com.cstv.app.data.remote.dto.CstvErrorDto
import com.cstv.app.domain.model.CstvError
import com.google.gson.Gson
import retrofit2.Response

class CstvErrorMapper(private val gson: Gson) {
    fun from(response: Response<*>): CstvError {
        return fromBody(response.code(), response.errorBody()?.string())
    }

    /** `errorBody` may be empty or malformed; an HTTP status remains a safe fallback. */
    fun fromBody(status: Int, errorBody: String?): CstvError = from(
        status,
        runCatching { gson.fromJson(errorBody, CstvErrorDto::class.java)?.code }.getOrNull()
    )

    fun from(status: Int, code: String?): CstvError = when (code) {
        "INVALID_OTP", "OTP_CONSUMED", "OTP_EXPIRED", "OTP_ATTEMPTS_EXCEEDED", "INVALID_OTP_FORMAT" -> CstvError.InvalidOtp
        "INVALID_EMAIL" -> CstvError.InvalidEmail
        "ACCOUNT_DISABLED" -> CstvError.Disabled
        "ACCOUNT_EXPIRED" -> CstvError.Expired
        "PROFILE_NOT_FOUND" -> CstvError.ProfileNotFound
        "ETAG_MISMATCH" -> CstvError.EtagMismatch
        "PAYLOAD_TOO_LARGE" -> CstvError.PayloadTooLarge
        "PRECONDITION_REQUIRED" -> CstvError.PreconditionRequired
        "INVALID_SCHEMA_VERSION", "UNSUPPORTED_MEDIA_TYPE" -> CstvError.Incompatible
        else -> when (status) {
            400, 422 -> CstvError.Unknown
            401 -> CstvError.Rejected
            403 -> CstvError.Disabled // fail closed
            404 -> CstvError.ProfileNotFound
            409 -> CstvError.LastProfile
            412 -> CstvError.EtagMismatch
            413 -> CstvError.PayloadTooLarge
            415 -> CstvError.Incompatible
            428 -> CstvError.PreconditionRequired
            429 -> CstvError.RateLimited
            in 500..599 -> CstvError.ServerError
            else -> CstvError.Unknown
        }
    }
}
