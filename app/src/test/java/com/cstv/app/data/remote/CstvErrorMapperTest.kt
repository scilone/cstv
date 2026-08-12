package com.cstv.app.data.remote

import com.cstv.app.domain.model.CstvError
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class CstvErrorMapperTest {
    private val mapper = CstvErrorMapper(Gson())

    @Test fun `json code has precedence over a misleading http status`() {
        assertEquals(CstvError.InvalidOtp, mapper.fromBody(400, "{\"code\":\"INVALID_OTP\"}"))
        assertEquals(CstvError.Expired, mapper.fromBody(403, "{\"code\":\"ACCOUNT_EXPIRED\"}"))
    }

    @Test fun `f34 status fallbacks are all represented`() {
        assertEquals(CstvError.ProfileNotFound, mapper.fromBody(404, null))
        assertEquals(CstvError.EtagMismatch, mapper.fromBody(412, null))
        assertEquals(CstvError.PayloadTooLarge, mapper.fromBody(413, null))
        assertEquals(CstvError.Incompatible, mapper.fromBody(415, null))
        assertEquals(CstvError.PreconditionRequired, mapper.fromBody(428, null))
        assertEquals(CstvError.ServerError, mapper.fromBody(503, "not json"))
    }

    @Test fun `table 4-6 status-only fallbacks`() {
        assertEquals(CstvError.RateLimited, mapper.fromBody(429, null))
        assertEquals(CstvError.Rejected, mapper.fromBody(401, null))
        assertEquals(CstvError.Disabled, mapper.fromBody(403, null))
        assertEquals(CstvError.LastProfile, mapper.fromBody(409, null))
        assertEquals(CstvError.ServerError, mapper.fromBody(500, null))
        assertEquals(CstvError.Unknown, mapper.fromBody(418, null))
    }

    @Test fun `an unrecognized 403 json code still fails closed as disabled`() {
        assertEquals(CstvError.Disabled, mapper.fromBody(403, "{\"code\":\"SOMETHING_NEW\"}"))
    }

    @Test fun `empty and truncated bodies fall back to the http status alone`() {
        assertEquals(CstvError.ServerError, mapper.fromBody(500, ""))
        assertEquals(CstvError.ServerError, mapper.fromBody(500, null))
        assertEquals(CstvError.RateLimited, mapper.fromBody(429, "{\"code\":"))
    }
}
