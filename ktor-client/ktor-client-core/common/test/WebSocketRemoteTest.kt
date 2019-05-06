/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.websocket

import io.ktor.client.features.logging.*
import io.ktor.client.tests.utils.*
import io.ktor.http.cio.websocket.*
import kotlinx.io.core.*
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
                logger = Logger.EMPTY
            }
            install(WebSockets)
        }

        test { client ->
            client.wss(host = echoWebsocket) {
                ping("hello, world")
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
