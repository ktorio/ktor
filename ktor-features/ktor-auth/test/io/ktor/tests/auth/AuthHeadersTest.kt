package io.ktor.tests.auth

import io.ktor.auth.*
import org.junit.Test
import kotlin.test.*

class AuthHeadersTest {
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