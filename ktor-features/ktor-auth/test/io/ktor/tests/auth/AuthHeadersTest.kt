package io.ktor.tests.auth

import io.ktor.auth.*
import org.junit.Test
import kotlin.test.*

class AuthHeadersTest {
    @Test
    fun testWithParameterParameterBehaviour() {
        val header = HttpAuthHeader.Parameterized("authScheme", mapOf("a" to "a"))
        // It uses singleOrNull, and since withParameter appends, it would be twice and would return null.
        // Verify if this is the expected behaviour.
        assertEquals(null, header.withParameter("a", "A").parameter("a"))
        assertEquals("B", header.withParameter("b", "B").parameter("b"))
    }

    @Test
    fun testWithParameterPreservesEncoding() {
        for (encoding in listOf(
            HeaderValueEncoding.QUOTED_ALWAYS, HeaderValueEncoding.URI_ENCODE
        )) {
            val header = HttpAuthHeader.Parameterized("authScheme", mapOf("a" to "b"), encoding)
            assertEquals(header.encoding, header.withParameter("a", "c").encoding)
        }
    }
}