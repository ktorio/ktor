/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.plugins.websocket.*
import io.ktor.client.test.base.*
import io.ktor.websocket.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

private const val FRAMES_COUNT = 100

private val NON_CALLBACK_BASED_WS_CLIENTS = arrayOf("CIO", "Darwin", "Java", "WinHttp")
private val CALLBACK_BASED_WS_CLIENTS = arrayOf("OkHttp", "JS", "Curl")

class WebSocketBackpressureTest : ClientLoader() {

    val Throwable?.isChannelOverflow: Boolean
        get() = this is ChannelOverflowException || this?.cause is ChannelOverflowException

    @Test
    fun `test IO frame channels suspension`() =
        clientTests(except(ENGINES_WITHOUT_WS + CALLBACK_BASED_WS_CLIENTS)) {
            config {
                install(WebSockets) {
                    channels {
                        incoming = bounded(capacity = 1, onOverflow = ChannelOverflow.SUSPEND)
                        outgoing = bounded(capacity = 1, onOverflow = ChannelOverflow.SUSPEND)
                    }
                }
            }

            test { client ->
                client.webSocket("$TEST_WEBSOCKET_SERVER/websockets/echo") {
                    val receivedAllFrames = CompletableDeferred<Unit>()
                    val sendJob = this@test.launch {
                        repeat(FRAMES_COUNT) { i -> send("message $i") }
                        // don't close the connection until all frames are received back
                        receivedAllFrames.await()
                        close()
                    }
                    val receiveJob = this@test.launch {
                        var expectedIndex = 0
                        for (frame in incoming) {
                            if (frame is Frame.Close) break
                            if (frame !is Frame.Text) continue
                            if (++expectedIndex == FRAMES_COUNT) break
                        }
                        assertEquals(FRAMES_COUNT, expectedIndex)
                        receivedAllFrames.complete(Unit)
                    }
                    listOf(sendJob, receiveJob).joinAll()
                }
            }
        }

    @Test
    fun `test IO frame channels suspension unsupported`() =
        clientTests(except(ENGINES_WITHOUT_WS, *NON_CALLBACK_BASED_WS_CLIENTS)) {
            config {
                install(WebSockets) {
                    channels {
                        incoming = bounded(capacity = 1, onOverflow = ChannelOverflow.SUSPEND)
                    }
                }
            }
            test { client ->
                assertFailsWith<IllegalArgumentException> {
                    client.webSocket("$TEST_WEBSOCKET_SERVER/websockets/echo") {
                        fail("Unreachable")
                    }
                }
            }
        }

    @Test
    fun `test incoming frame channel overflow`() = clientTests(except(ENGINES_WITHOUT_WS)) {
        config {
            install(WebSockets) {
                channels {
                    incoming = bounded(capacity = 1, onOverflow = ChannelOverflow.CLOSE)
                }
            }
        }

        test { client ->
            try {
                client.webSocket("$TEST_WEBSOCKET_SERVER/websockets/receive-backpressure") {
                    val incomingChannelClosed = CompletableDeferred<Unit>()
                    // Cast it only for testing purposes!
                    (incoming as Channel<*>).invokeOnClose {
                        assertTrue(it.isChannelOverflow, "Unexpected exception: $it")
                        incomingChannelClosed.complete(Unit)
                    }
                    withTimeout(10.seconds) {
                        incomingChannelClosed.await()
                    }
                    close()
                }
            } catch (e: Exception) {
                // may be thrown if propagated from ws
                assertTrue(e.isChannelOverflow, "Unexpected exception: $e")
            }
        }
    }

    @Test
    fun `test outgoing frame channel overflow`() = clientTests(except(ENGINES_WITHOUT_WS)) {
        config {
            install(WebSockets) {
                channels {
                    outgoing = bounded(capacity = 1, onOverflow = ChannelOverflow.CLOSE)
                }
            }
        }

        test { client ->
            client.webSocket("$TEST_WEBSOCKET_SERVER/websockets/echo") {
                val outgoingChannelClosed = CompletableDeferred<Unit>()
                outgoing.invokeOnClose {
                    assertTrue(it.isChannelOverflow, "Unexpected exception: $it")
                    outgoingChannelClosed.complete(Unit)
                }

                // Fill the outgoing buffer beyond capacity without waiting
                runCatching {
                    for (i in 1..FRAMES_COUNT) send("message $i")
                }.onFailure {
                    assertTrue(it.isChannelOverflow, "Unexpected exception: $it")
                }.onSuccess {
                    fail("Expected overflow exception but got success")
                }
                withTimeout(1.seconds) {
                    outgoingChannelClosed.await()
                }
            }
        }
    }
}
