/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.testing

import io.ktor.client.plugins.websocket.cio.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets

class TestEngineWebSocketTest {

    @Test
    fun `close reply sent after the conversation ends reaches the client`() = testApplication {
        install(WebSockets)

        val replyGate = CompletableDeferred<Unit>()
        routing {
            webSocketRaw("/") {
                incoming.receive()
                replyGate.await()
                outgoing.send(Frame.Close(CloseReason(CloseReason.Codes.NORMAL, "bye")))
            }
        }
        startApplication()

        lateinit var clientIncoming: ReceiveChannel<Frame>
        engine.handleWebSocketConversation("/", awaitCallback = true) { incoming, outgoing ->
            clientIncoming = incoming
            outgoing.send(Frame.Close(CloseReason(CloseReason.Codes.NORMAL, "done")))
        }
        replyGate.complete(Unit)

        val reply = withTimeout(5.seconds) { clientIncoming.receive() }
        assertIs<Frame.Close>(reply)
        assertEquals("bye", reply.readReason()?.message)
    }

    @Test
    fun `raw session stays open until closed`() = testApplication {
        install(WebSockets)

        routing {
            webSocketRaw("/") {
                for (frame in incoming) {
                    outgoing.send(frame)
                }
            }
        }

        val client = createClient { install(ClientWebSockets) }
        val session = client.webSocketRawSession(path = "/")

        val ended = withTimeoutOrNull(1.seconds) { session.coroutineContext.job.join() }
        assertNull(ended, "The session ended while it was still open")

        session.send(Frame.Text("hello"))
        val echo = withTimeout(5.seconds) { session.incoming.receive() }
        assertEquals("hello", assertIs<Frame.Text>(echo).readText())
        session.close()
    }
}
