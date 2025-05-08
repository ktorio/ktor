/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.auth

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BearerProviderTest {
    private val testScope = TestScope()

    @Test
    fun testCachingByDefault() = testScope.runTest {
        val tokenStorage = mutableListOf(BearerTokens("initial-token", "refresh-token"))
        var loadCount = 0

        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel("""{ "status": "ok" }"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine) {
            install(Auth) {
                bearer {
                    loadTokens {
                        loadCount++
                        tokenStorage.firstOrNull()
                    }
                }
            }
        }

        // First request - should load token
        client.get("https://example.com").bodyAsText()
        assertEquals(1, loadCount)
        assertEquals("Bearer initial-token", mockEngine.requestHistory[0].headers[HttpHeaders.Authorization])

        // Second request - should use cached token
        client.get("https://example.com").bodyAsText()
        assertEquals(1, loadCount) // Load count should still be 1 since token was cached
        assertEquals("Bearer initial-token", mockEngine.requestHistory[1].headers[HttpHeaders.Authorization])

        // Update tokens externally
        tokenStorage.clear()
        tokenStorage.add(BearerTokens("new-token", "new-refresh-token"))

        // Third request - should still use cached token
        client.get("https://example.com").bodyAsText()
        assertEquals(1, loadCount) // Load count should still be 1
        assertEquals("Bearer initial-token", mockEngine.requestHistory[2].headers[HttpHeaders.Authorization])

        // Clear token cache and try again
        client.authProvider<BearerAuthProvider>()?.clearToken()

        // Fourth request - should load new token
        client.get("https://example.com").bodyAsText()
        assertEquals(2, loadCount) // Load count should now be 2
        assertEquals("Bearer new-token", mockEngine.requestHistory[3].headers[HttpHeaders.Authorization])
    }

    @Test
    fun testDisabledCaching() = testScope.runTest {
        val tokenStorage = mutableListOf(BearerTokens("initial-token", "refresh-token"))
        var loadCount = 0

        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel("""{ "status": "ok" }"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine) {
            install(Auth) {
                bearer {
                    loadTokens {
                        loadCount++
                        tokenStorage.firstOrNull()
                    }
                    cacheTokens = false
                }
            }
        }

        // First request - should load token
        client.get("https://example.com").bodyAsText()
        assertEquals(1, loadCount)
        assertEquals("Bearer initial-token", mockEngine.requestHistory[0].headers[HttpHeaders.Authorization])

        // Second request - should load token again
        client.get("https://example.com").bodyAsText()
        assertEquals(2, loadCount) // Load count should be 2 since token was not cached
        assertEquals("Bearer initial-token", mockEngine.requestHistory[1].headers[HttpHeaders.Authorization])

        // Update tokens externally
        tokenStorage.clear()
        tokenStorage.add(BearerTokens("new-token", "new-refresh-token"))

        // Third request - should load new token
        client.get("https://example.com").bodyAsText()
        assertEquals(3, loadCount) // Load count should be 3
        assertEquals("Bearer new-token", mockEngine.requestHistory[2].headers[HttpHeaders.Authorization])
    }

    @Test
    fun testCustomTokenStorage() = testScope.runTest {
        val tokenStorage = mutableListOf(BearerTokens("initial-token", "refresh-token"))
        var loadCount = 0

        val customStorage = object : TokenStorage<BearerTokens> {
            var currentToken: BearerTokens? = null

            override suspend fun loadToken(): BearerTokens? {
                if (currentToken != null) return currentToken

                loadCount++
                val token = tokenStorage.firstOrNull()
                currentToken = token
                return token
            }

            override suspend fun updateToken(block: suspend () -> BearerTokens?): BearerTokens? {
                currentToken = block()
                return currentToken
            }

            override suspend fun clearToken() {
                currentToken = null
            }
        }

        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel("""{ "status": "ok" }"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine) {
            install(Auth) {
                bearer {
                    // This loadTokens should never be called
                    loadTokens { throw Exception("Should not be called") }
                    tokenStorage(customStorage)
                }
            }
        }

        // First request - should load token
        client.get("https://example.com").bodyAsText()
        assertEquals(1, loadCount)
        assertEquals("Bearer initial-token", mockEngine.requestHistory[0].headers[HttpHeaders.Authorization])

        // Second request - should use cached token
        client.get("https://example.com").bodyAsText()
        assertEquals(1, loadCount) // Load count should still be 1 since token was cached
        assertEquals("Bearer initial-token", mockEngine.requestHistory[1].headers[HttpHeaders.Authorization])

        // Update tokens externally
        tokenStorage.clear()
        tokenStorage.add(BearerTokens("new-token", "new-refresh-token"))

        // Clear token cache and try again
        client.authProvider<BearerAuthProvider>()?.clearToken()

        // Third request - should load new token
        client.get("https://example.com").bodyAsText()
        assertEquals(2, loadCount) // Load count should now be 2
        assertEquals("Bearer new-token", mockEngine.requestHistory[2].headers[HttpHeaders.Authorization])
    }

    @Test
    fun testRefreshFlow() = testScope.runTest {
        val tokenStorage = mutableListOf(BearerTokens("expired-token", "refresh-token"))
        var refreshCalled = false

        val mockEngine = MockEngine { request ->
            val token = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")

            if (token == "expired-token") {
                respond(
                    content = ByteReadChannel("Unauthorized"),
                    status = HttpStatusCode.Unauthorized,
                    headers = headersOf(HttpHeaders.WWWAuthenticate, "Bearer")
                )
            } else {
                respond(
                    content = ByteReadChannel("""{ "status": "ok" }"""),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val client = HttpClient(mockEngine) {
            install(Auth) {
                bearer {
                    loadTokens {
                        tokenStorage.firstOrNull()
                    }
                    refreshTokens {
                        refreshCalled = true
                        val newToken = BearerTokens("new-token", "refresh-token")
                        tokenStorage.clear()
                        tokenStorage.add(newToken)
                        newToken
                    }
                }
            }
        }

        // Request should trigger a refresh and retry
        val response = client.get("https://example.com")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(2, mockEngine.requestHistory.size) // Should have 2 requests (initial + retry)
        assertTrue(refreshCalled)
        assertEquals("Bearer new-token", mockEngine.requestHistory[1].headers[HttpHeaders.Authorization])
    }
}
