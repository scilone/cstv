package com.cstv.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

class ServerAddressParserTest {
    // Filet anti-blocage : un test coroutine qui boucle sur le scheduler virtuel
    // (tâche périodique inconditionnelle dans un `init` de ViewModel, boucle de
    // pagination dont le mock renvoie toujours une page pleine) fige le build
    // sans jamais échouer. Cette règle nomme le test fautif ; le garde-fou dur
    // est `tasks.withType<Test> { timeout }` dans app/build.gradle.kts.
    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)


    private fun assertSuccess(input: String, expectedHost: String, expectedPort: Int) {
        val result = ServerAddressParser.parse(input)
        assertTrue("Expected Success for \"$input\" but got $result", result is ServerAddressParser.Result.Success)
        result as ServerAddressParser.Result.Success
        assertEquals(expectedHost, result.host)
        assertEquals(expectedPort, result.port)
    }

    private fun assertError(input: String) {
        val result = ServerAddressParser.parse(input)
        assertTrue("Expected Error for \"$input\" but got $result", result is ServerAddressParser.Result.Error)
    }

    @Test
    fun test_parse_addsHttpScheme_whenMissing() {
        assertSuccess("mondns.com:8080", "http://mondns.com", 8080)
    }

    @Test
    fun test_parse_preservesHttpScheme_whenPresent() {
        assertSuccess("http://mondns.com:8080", "http://mondns.com", 8080)
    }

    @Test
    fun test_parse_preservesHttpsScheme_whenPresent() {
        assertSuccess("https://mondns.com:8080", "https://mondns.com", 8080)
    }

    @Test
    fun test_parse_handlesIpAddress() {
        assertSuccess("192.168.1.1:25461", "http://192.168.1.1", 25461)
    }

    @Test
    fun test_parse_isCaseInsensitiveOnScheme() {
        assertSuccess("HTTPS://mondns.com:443", "https://mondns.com", 443)
    }

    @Test
    fun test_parse_trimsWhitespace() {
        assertSuccess("  mondns.com:8080  ", "http://mondns.com", 8080)
    }

    @Test
    fun test_parse_ignoresTrailingPath() {
        assertSuccess("http://mondns.com:8080/xtream/", "http://mondns.com", 8080)
    }

    @Test
    fun test_parse_returnsError_whenBlank() {
        assertError("")
        assertError("   ")
    }

    @Test
    fun test_parse_returnsError_whenPortMissing() {
        assertError("mondns.com")
        assertError("http://mondns.com")
    }

    @Test
    fun test_parse_returnsError_whenPortNonNumeric() {
        assertError("mondns.com:abcd")
    }

    @Test
    fun test_parse_returnsError_whenPortOutOfRange() {
        assertError("mondns.com:0")
        assertError("mondns.com:99999")
    }

    @Test
    fun test_parse_returnsError_whenHostMissing() {
        assertError(":8080")
    }

    @Test
    fun test_buildDisplayAddress_addsDefaultHttpScheme() {
        assertEquals("http://mondns.com:8080", ServerAddressParser.buildDisplayAddress("mondns.com", 8080))
    }

    @Test
    fun test_buildDisplayAddress_preservesExistingScheme() {
        assertEquals("https://mondns.com:8080", ServerAddressParser.buildDisplayAddress("https://mondns.com", 8080))
    }

    @Test
    fun test_buildDisplayAddress_omitsPort_whenZeroOrNegative() {
        assertEquals("http://mondns.com", ServerAddressParser.buildDisplayAddress("mondns.com", 0))
    }

    @Test
    fun test_buildDisplayAddress_returnsEmpty_whenHostBlank() {
        assertEquals("", ServerAddressParser.buildDisplayAddress("", 8080))
    }
}
