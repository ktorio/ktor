/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http

import io.ktor.http.auth.*
import kotlin.test.*

class AuthHeaderParseTest {

    @Test
    fun testParameter() {
        val header = """
            Bearer realm=Hell
        """.trimIndent()

        val result = parseAuthorizationHeader(header)
        assertTrue(result is HttpAuthHeader.Parameterized)

        assertEquals("Bearer", result.authScheme)
        assertEquals("Hell", result.parameter("realm"))
    }

    @Test
    fun testQuotedParameter() {
        val header = """
            Bearer realm="Hello"
        """.trimIndent()

        val result = parseAuthorizationHeader(header)
        assertTrue(result is HttpAuthHeader.Parameterized)

        assertEquals("Bearer", result.authScheme)
        assertEquals("Hello", result.parameter("realm"))
    }

    @Test
    fun testMultipleParameters() {
        val header = """
            Bearer realm="Hell", x = world
        """.trimIndent()

        val result = parseAuthorizationHeader(header)
        assertTrue(result is HttpAuthHeader.Parameterized)

        assertEquals("Bearer", result.authScheme)
        assertEquals("Hell", result.parameter("realm"))
        assertEquals("world", result.parameter("x"))
    }

    @Test
    fun testSingleParameterWithComma() {
        val header = """
            Bearer realm="Hell, x = world"
        """.trimIndent()

        val result = parseAuthorizationHeader(header)
        assertTrue(result is HttpAuthHeader.Parameterized)

        assertEquals("Bearer", result.authScheme)
        assertEquals("Hell, x = world", result.parameter("realm"))
    }

    @Test
    fun testBasicSchema() {
        val header = """
            Basic YWxhZGRpbjpvcGVuc2VzYW1l
        """.trimIndent()

        val result = parseAuthorizationHeader(header)

        assertTrue(result is HttpAuthHeader.Single)
        assertEquals("Basic", result.authScheme)
        assertEquals("YWxhZGRpbjpvcGVuc2VzYW1l", result.blob)
    }

    @Test
    fun testParametersWithoutSpace() {
        val header = """
            Basic realm=oauth2, charset=UTF-8 
        """.trimIndent()

        val result = parseAuthorizationHeader(header)
        assertTrue(result is HttpAuthHeader.Parameterized)
        assertEquals("oauth2", result.parameter("realm"))
        assertEquals("UTF-8", result.parameter("charset"))
    }
}
