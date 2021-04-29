/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/
package io.ktor.client.tests.features

import io.ktor.client.features.logging.*
import io.ktor.client.features.websocket.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.test.*

class WebSocketRemoteTest : ClientLoader() {
    private val echoWebsocket = "$TEST_WEBSOCKET_SERVER/websockets/echo"
    private val skipEngines = listOf("Android", "Apache")

    @Test
    fun testRemotePingPong() = clientTests(skipEngines) {
        config {
            install(WebSockets)
        }

        test { client ->
            client.ws(echoWebsocket) {
                repeat(10) {
                    ping(it.toString())
                }
            }
        }
    }

    @Test
    @Ignore
    fun testSecureRemotePingPong() = clientTests(skipEngines) {
        config {
            install(WebSockets)
        }

        test { client ->
            client.wss(echoWebsocket) {
                repeat(10) {
                    ping(it.toString())
                }
            }
        }
    }

    @Test
    fun testWithLogging() = clientTests(skipEngines) {
        config {
            install(Logging) {
                level = LogLevel.ALL
                logger = Logger.EMPTY
            }
            install(WebSockets)
        }

        test { client ->
            client.wss(echoWebsocket) {
                ping("hello, world")
            }
        }
    }

    @Test
    fun testSessionClose() = clientTests(skipEngines) {
        config {
            install(WebSockets)
        }

        test { client ->
            val session = client.webSocket(echoWebsocket) {
                close(CloseReason(CloseReason.Codes.NORMAL, "OK"))
            }
        }
    }

    @Test
    fun testSessionTermination() = clientTests(skipEngines) {
        config {
            install(WebSockets)
        }

        test { client ->
            client.webSocket(echoWebsocket) {
                cancel()
            }
        }
    }

    @Test
    fun testBadCloseReason() = clientTests(skipEngines) {
        config {
            install(WebSockets)
        }

        test { client ->
            client.webSocket(echoWebsocket) {
                close(CloseReason(1005, "Reserved close code"))
            }
        }
    }

    private suspend fun WebSocketSession.ping(salt: String) {
        outgoing.send(Frame.Text("text: $salt"))
        val frame = incoming.receive()
        check(frame is Frame.Text)

        assertEquals("text: $salt", frame.readText())

        val data = "text: $salt".toByteArray()
        outgoing.send(Frame.Binary(true, data))
        val binaryFrame = incoming.receive()
        check(binaryFrame is Frame.Binary)

        val buffer = binaryFrame.data
        assertEquals(data.contentToString(), buffer.contentToString())
    }
}
