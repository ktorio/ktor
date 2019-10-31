/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.client.tests.features

import io.ktor.client.features.logging.*
import io.ktor.client.features.websocket.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.util.logging.Logger
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.test.*

class WebSocketRemoteTest : ClientLoader() {
    private val echoWebsocket = "echo.websocket.org"

    @Test
    fun testRemotePingPong() = clientTests {
        config {
            install(WebSockets)
        }

        test { client ->
            client.ws(host = echoWebsocket) {
                repeat(10) {
                    ping(it.toString())
                }
            }
        }
    }

    @Test
    fun testSecureRemotePingPong() = clientTests {
        config {
            install(WebSockets)
        }

        test { client ->
            client.wss(host = echoWebsocket) {
                repeat(10) {
                    ping(it.toString())
                }
            }
        }
    }

    @Test
    fun testWithLogging() = clientTests {
        config {
            install(Logging) {
                level = LogLevel.ALL
                logger = Logger.Muted
            }
            install(WebSockets)
        }

        test { client ->
            client.wss(host = echoWebsocket) {
                ping("hello, world")
            }
        }
    }

    @Test
    fun testSessionClose() = clientTests {
        config {
            install(WebSockets)
        }

        test { client ->
            val session = client.webSocket(host = echoWebsocket, request = {
                url.protocol = URLProtocol.WSS
                url.port = DEFAULT_PORT
            }) {
                close(CloseReason(CloseReason.Codes.NORMAL, "OK"))
            }
        }
    }

    @Test
    fun testSessionTermination() = clientTests {
        config {
            install(WebSockets)
        }

        test { client ->
            client.webSocket(host = echoWebsocket, request = {
                url.protocol = URLProtocol.WSS
                url.port = DEFAULT_PORT
            }) {
                cancel()
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
