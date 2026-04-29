/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.curl.test

import io.ktor.client.engine.curl.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.test.base.*
import io.ktor.websocket.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CurlNativeTests : ClientEngineTest<CurlClientEngineConfig>(Curl) {

    @Test
    fun testDownload() = testClient {
        test { client ->
            val response = client.get("$TEST_SERVER/content/hello")
            assertEquals("hello", response.bodyAsText())
        }
    }

    @Test
    fun testDelete(): Unit = testClient {
        test { client ->
            val response = client.delete("$TEST_SERVER/delete")
            assertEquals("OK ", response.bodyAsText())

            val responseWithBody = client.delete("$TEST_SERVER/delete") {
                setBody("1")
            }
            assertEquals("OK 1", responseWithBody.bodyAsText())
        }
    }

    /**
     * Regression test for KTOR-9540 (ABA pointer-reuse bug in Curl WebSocket cancellation).
     *
     * When a WebSocket upgrade is rejected by the server (e.g., 401 Unauthorized), the engine
     * must not create a CurlWebSocketSession for the rejected response. Previously, the engine
     * would create a session for any upgrade request regardless of the status code, which could
     * lead to cancelWebSocket being called with a stale easy handle that was already reused for
     * the retry request (ABA bug).
     *
     * This test verifies that the engine correctly handles a rejected upgrade followed by a
     * successful retry with refreshed credentials, without crashing or corrupting state.
     */
    @Test
    fun testWebSocketAuthRetryAfterRejectedUpgrade() = testClient {
        config {
            install(WebSockets)
            install(Auth) {
                bearer {
                    loadTokens { BearerTokens("invalid", "invalid") }
                    refreshTokens { BearerTokens("valid", "valid") }
                }
            }
        }
        test { client ->
            // First attempt sends "invalid" token, server returns 401 (rejected upgrade).
            // Engine must not create a stale WebSocket session for the 401 response.
            // Auth plugin retries with "valid" token, server accepts and upgrades.
            client.webSocket("$TEST_WEBSOCKET_SERVER/auth/websocket") {
                val frame = incoming.receive() as Frame.Text
                assertEquals("Hello from server", frame.readText())
            }
        }
    }

    /**
     * Regression test for KTOR-9540: Verifies that when the server rejects the WebSocket
     * upgrade and no valid refresh token is available, the engine returns a proper error
     * instead of hanging or panicking. The rejected upgrade path must return ByteReadChannel.Empty
     * as the response body (not a CurlWebSocketSession).
     */
    @Test
    fun testWebSocketAuthFullyRejectedUpgrade() = testClient {
        config {
            install(WebSockets)
            install(Auth) {
                bearer {
                    loadTokens { BearerTokens("invalid", "invalid") }
                    refreshTokens { BearerTokens("invalid", "invalid") }
                }
            }
        }
        test { client ->
            // Both the initial and refreshed tokens are invalid, so the server always returns
            // 401. The engine must propagate this as a WebSocketException, not crash.
            assertFailsWith<WebSocketException> {
                client.webSocket("$TEST_WEBSOCKET_SERVER/auth/websocket") {}
            }
        }
    }
}
