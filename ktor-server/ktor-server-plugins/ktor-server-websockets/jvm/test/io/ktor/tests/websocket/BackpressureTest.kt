/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.websocket

import io.ktor.client.plugins.websocket.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.server.websocket.*
import io.ktor.server.websocket.WebSockets
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets

private const val FRAMES_COUNT = 100

abstract class BackpressureTest {

    abstract fun Route.createSession(url: String, handler: suspend WebSocketServerSession.() -> Unit)

    private inline fun expectChannelOverflow(crossinline block: () -> Unit) {
        runCatching { block() }
            .onFailure { assertTrue(it is ChannelOverflowException || it.cause is ChannelOverflowException) }
            .onSuccess { fail("Expected overflow exception but got success") }
    }

    @Test
    fun testSessionBackpressure() = testApplication {
        install(WebSockets) {
            channels {
                incoming = bounded(capacity = 1, onOverflow = ChannelOverflow.SUSPEND)
                outgoing = bounded(capacity = 1, onOverflow = ChannelOverflow.SUSPEND)
            }
        }

        routing {
            createSession("/echo") {
                for (frame in incoming) {
                    if (frame is Frame.Close) break
                    send(frame.copy())
                }
            }
        }

        createClient {
            install(ClientWebSockets)
        }.use { client ->
            client.webSocket("/echo") {
                val receiveJob = launch {
                    var expectedIndex = 1
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            assertEquals("message ${expectedIndex++}", frame.readText())
                        }
                        if (expectedIndex == FRAMES_COUNT + 1) break
                    }
                    assertEquals(FRAMES_COUNT + 1, expectedIndex)
                }
                for (i in 1..FRAMES_COUNT) {
                    send("message $i")
                }
                receiveJob.join()
            }
        }
    }

    @Test
    fun testSessionOutgoingOverflow() = testApplication {
        install(WebSockets) {
            channels {
                outgoing = bounded(capacity = 1, onOverflow = ChannelOverflow.CLOSE)
            }
        }
        routing {
            expectChannelOverflow {
                createSession("/receive-all") {
                    for (i in 1..FRAMES_COUNT) send("message $i")
                }
            }
        }

        createClient {
            install(ClientWebSockets)
        }.use { client ->
            runCatching {
                client.webSocket("/receive-all") {
                    incoming.consumeEach { }
                }
            }
        }
    }

    @Test
    fun testSessionIncomingOverflow() = testApplication {
        install(WebSockets) {
            channels {
                incoming = bounded(capacity = 1, onOverflow = ChannelOverflow.CLOSE)
            }
        }
        routing {
            expectChannelOverflow {
                createSession("/incoming-overflow") {
                    delay(10.seconds)
                }
            }
        }

        createClient { install(ClientWebSockets) }.use { client ->
            runCatching {
                client.webSocket("/incoming-overflow") {
                    for (i in 1..FRAMES_COUNT) send("message $i")
                    close()
                }
            }
        }
    }
}

class DefaultBackpressureTest : BackpressureTest() {
    override fun Route.createSession(url: String, handler: suspend WebSocketServerSession.() -> Unit) {
        webSocket(path = url, handler = handler)
    }
}

class RawBackpressureTest : BackpressureTest() {
    override fun Route.createSession(url: String, handler: suspend WebSocketServerSession.() -> Unit) {
        webSocketRaw(path = url, handler = handler)
    }
}
