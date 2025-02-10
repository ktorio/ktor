/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.auth

import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BasicProviderTest {
    @Test
    fun testUnicodeCredentials() {
        assertEquals(
            "Basic VW1sYXV0ZcOEw7zDtjphJlNlY3JldCUhMjM=",
            buildAuthString("UmlauteÄüö", "a&Secret%!23")
        )
    }

    @Test
    fun testLoginWithColon() {
        assertEquals(
            "Basic dGVzdDo0NzExOmFwYXNzd29yZA==",
            buildAuthString("test:4711", "apassword")
        )
    }

    @Test
    fun testSimpleCredentials() {
        assertEquals(
            "Basic YWRtaW46YWRtaW4=",
            buildAuthString("admin", "admin")
        )
    }

    @Test
    fun testCapitalizedSchemeIsApplicable() {
        val provider = BasicAuthProvider(credentials = {
            BasicAuthCredentials("user", "password")
        })
        val header = parseAuthorizationHeader("BASIC realm=\"ktor\"")
        assertNotNull(header)

        assertTrue(provider.isApplicable(header), "Provider with capitalized scheme should be applicable")
    }

    @OptIn(InternalAPI::class)
    @Test
    fun `update credentials after clearToken`() = runTest {
        var credentials = BasicAuthCredentials("admin", "admin")
        val provider = BasicAuthProvider(credentials = { credentials })

        val requestBuilder = HttpRequestBuilder()
        suspend fun assertAuthorizationHeader(expected: String) {
            provider.addRequestHeaders(requestBuilder, authHeader = null)
            assertEquals(expected, requestBuilder.headers[HttpHeaders.Authorization])
        }

        assertAuthorizationHeader("Basic YWRtaW46YWRtaW4=")
        credentials = BasicAuthCredentials("user", "qwerty")
        assertAuthorizationHeader("Basic YWRtaW46YWRtaW4=")
        provider.clearToken()
        assertAuthorizationHeader("Basic dXNlcjpxd2VydHk=")
    }

    private fun buildAuthString(username: String, password: String): String =
        constructBasicAuthValue(BasicAuthCredentials(username, password))
}
