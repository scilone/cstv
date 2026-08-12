package com.cstv.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CstvEtagTest {
    @Test fun `normalizes listing and header etags`() {
        assertEquals("abc", CstvEtag.bare("\"abc\""))
        assertEquals("\"abc\"", CstvEtag.header("abc"))
    }
    @Test fun `empty etag stays absent`() { assertNull(CstvEtag.bare("  ")) }
    @Test fun `weak and absent etags normalize safely`() {
        assertEquals("abc", CstvEtag.bare("W/\"abc\""))
        assertNull(CstvEtag.bare(null)); assertNull(CstvEtag.header(null))
    }
}
