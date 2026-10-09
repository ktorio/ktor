/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class WebSocketFailedHandshakeTest {
    private fun clientRespondingWith(body: String, @Suppress("SameParameterValue") contentLength: Int) =
        HttpClient(MockEngine) {
            install(WebSockets)
            engine {
                addHandler {
                    respond(
                        ByteReadChannel(body),
                        HttpStatusCode.Forbidden,
                        headersOf(HttpHeaders.ContentLength, contentLength.toString())
                    )
                }
            }
        }

    private suspend fun HttpClient.failedHandshake(): WebSocketHandshakeException =
        assertFailsWith<WebSocketHandshakeException> {
            webSocket("ws://localhost/ws") { fail("Unreachable") }
        }

    @Test
    fun `exposes body of failed handshake`() = runTest {
        val exception = clientRespondingWith("forbidden", contentLength = 9).failedHandshake()

        val response = assertNotNull(exception.response)
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals("forbidden", response.bodyAsText())
        assertTrue(exception.suppressedExceptions.isEmpty())
    }

    @Test
    fun `exposes failed handshake with missing body despite Content-Length`() = runTest {
        val exception = clientRespondingWith("", contentLength = 9).failedHandshake()

        val response = assertNotNull(exception.response)
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals("", response.bodyAsText())
    }

    @Test
    fun `does not expose truncated body of failed handshake`() = runTest {
        val exception = clientRespondingWith("forb", contentLength = 9).failedHandshake()

        assertNull(exception.response)
        val saveFailure = assertIs<IllegalStateException>(exception.suppressedExceptions.single())
        assertContains(saveFailure.message.orEmpty(), "Content-Length mismatch")
    }
}
