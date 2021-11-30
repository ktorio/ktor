/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.plugins

import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.tests.utils.*
import io.ktor.client.tests.utils.assertFailsWith
import io.ktor.websocket.*
import kotlin.test.*

class WebsocketTest : ClientLoader() {
    private val echoWebsocket = "$TEST_WEBSOCKET_SERVER/websockets/echo"

    private class CustomException : Exception()

    @Test
    fun testErrorHandling() = clientTests(listOf("Android", "Apache", "Curl")) {
        config {
            install(WebSockets)
        }

        test { client ->
//            assertFailsWith<CustomException> {
            try {
                client.wss(echoWebsocket) {
                    outgoing.send(Frame.Text("Hello"))
                    val frame = incoming.receive()
                    check(frame is Frame.Text)

                    throw CustomException()
                }
            } catch (cause: Throwable) {
                if (cause !is CustomException && cause.message?.contains(CustomException::class.simpleName!!) == true) {
                    error("Expected ${CustomException::class} exception, but $cause was thrown instead")
                }
                return@test
            }
//            }
        }
    }

    @Test
    fun testAssert() = clientTests(listOf("Android", "Apache", "Curl")) {
        config {
            install(WebSockets)
        }

        test { client ->
            assertFailsWith<CustomException> {
                throw CustomException()
            }
        }
    }

    @Test
    fun testAssert1() = clientTests(listOf("Android", "Apache", "Curl")) {
        config {
            install(WebSockets)
        }

        test { client ->
            assertFailsWith<CustomException> {
                client.wss(echoWebsocket) {
                    throw CustomException()
                }
            }
        }
    }

    @Test
    fun testAssert2() = clientTests(listOf("Android", "Apache", "Curl")) {
        config {
            install(WebSockets)
        }

        test { client ->
            assertFailsWith<CustomException> {
                client.webSocket(echoWebsocket) {
                    throw CustomException()
                }
            }
        }
    }
}
