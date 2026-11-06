/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.websocket

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.server.websocket.*
import io.ktor.server.websocket.WebSockets
import io.ktor.websocket.*
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets

/**
 * Tests for KTOR-6686: a server WebSocket handler must not outlive its connection.
 *
 * Once the peer closed the connection, a handler that never reads [incoming] and never sends
 * frames (e.g. it collects a shared flow waiting for data changes to push) used to keep running
 * forever: neither its `catch` nor its `finally` block were executed.
 */
class WebSocketHandlerTerminationTest {

    private fun ApplicationTestBuilder.createWebSocketsClient(): HttpClient = createClient {
        install(ClientWebSockets)
    }

    @Test
    fun testHandlerIsCancelledAfterClientClosesConnection() = testApplication {
        val handlerResult = CompletableDeferred<String>()

        install(WebSockets)

        routing {
            webSocket("/ws") {
                try {
                    // Mimic the ticket scenario: the handler neither reads incoming nor sends frames,
                    // it just waits for data to appear (e.g. collecting a MutableSharedFlow).
                    while (true) {
                        delay(100)
                    }
                } catch (cause: CancellationException) {
                    handlerResult.complete("cancelled")
                    throw cause
                } catch (cause: Throwable) {
                    handlerResult.complete("failed:${cause::class.simpleName}")
                    throw cause
                } finally {
                    handlerResult.complete("finally")
                }
            }
        }

        createWebSocketsClient().webSocket("/ws") {
            close(CloseReason(CloseReason.Codes.NORMAL, "bye"))
        }

        val result = withTimeout(10.seconds) { handlerResult.await() }
        assertEquals(
            "cancelled",
            result,
            "The server handler must be cancelled once the client closed the connection"
        )
    }

    @Test
    fun testHandlerReadingIncomingUntilCloseCompletesNormally() = testApplication {
        val handlerResult = CompletableDeferred<String>()
        val received = mutableListOf<String>()

        install(WebSockets)

        routing {
            webSocket("/ws") {
                try {
                    // The canonical pattern: consume frames until the peer closes the session
                    for (frame in incoming) {
                        if (frame is Frame.Text) received.add(frame.readText())
                    }
                    handlerResult.complete("completed")
                } catch (cause: CancellationException) {
                    handlerResult.complete("cancelled")
                    throw cause
                } finally {
                    handlerResult.complete("finally")
                }
            }
        }

        createWebSocketsClient().webSocket("/ws") {
            send(Frame.Text("one"))
            send(Frame.Text("two"))
            close(CloseReason(CloseReason.Codes.NORMAL, "bye"))
        }

        val result = withTimeout(10.seconds) { handlerResult.await() }
        assertEquals(listOf("one", "two"), received)
        assertEquals("completed", result)
    }

    @Test
    fun testHandlerInitiatedCloseStillWorks() = testApplication {
        val handlerResult = CompletableDeferred<String>()
        var closeReasonFromClient: CloseReason? = null

        install(WebSockets)

        routing {
            webSocket("/ws") {
                try {
                    val frame = incoming.receive() as? Frame.Text ?: error("Expected a text frame")
                    send(Frame.Text(frame.readText().uppercase()))
                    close(CloseReason(CloseReason.Codes.NORMAL, "server done"))
                    handlerResult.complete("completed")
                } catch (cause: CancellationException) {
                    handlerResult.complete("cancelled")
                    throw cause
                } finally {
                    handlerResult.complete("finally")
                }
            }
        }

        createWebSocketsClient().webSocket("/ws") {
            send(Frame.Text("hello"))
            for (frame in incoming) {
                if (frame is Frame.Text) assertEquals("HELLO", frame.readText())
            }
            closeReasonFromClient = closeReason.await()
        }

        val result = withTimeout(10.seconds) { handlerResult.await() }
        val reason = requireNotNull(closeReasonFromClient) { "The server did not send a close reason" }
        assertEquals(CloseReason(CloseReason.Codes.NORMAL, "server done"), reason)
        assertEquals("completed", result)
    }

    @Test
    fun testSecondClientIsNotAffectedByFirstClientClosingSilently() = testApplication {
        install(WebSockets)

        val firstClientConnected = AtomicBoolean(false)
        val firstClientHandlerFinished = CompletableDeferred<String>()
        val secondClientReceived = CompletableDeferred<String>()

        routing {
            webSocket("/ws") {
                if (firstClientConnected.compareAndSet(false, true)) {
                    // First client's handler never reads incoming - it must be cancelled on close
                    try {
                        while (true) delay(100)
                    } catch (cause: CancellationException) {
                        firstClientHandlerFinished.complete("cancelled")
                        throw cause
                    } finally {
                        firstClientHandlerFinished.complete("finally")
                    }
                } else {
                    secondClientReceived.complete((incoming.receive() as Frame.Text).readText())
                }
            }
        }

        val client = createWebSocketsClient()
        client.webSocket("/ws") {
            // make sure the server handler is up before closing the connection
            delay(500)
            close(CloseReason(CloseReason.Codes.NORMAL, "first is gone"))
        }

        assertEquals("cancelled", firstClientHandlerFinished.await())

        client.webSocket("/ws") {
            send(Frame.Text("still alive"))
        }

        assertEquals("still alive", withTimeout(10.seconds) { secondClientReceived.await() })
    }
}
