/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.plugins

import io.ktor.client.plugins.websocket.*
import io.ktor.client.tests.utils.*
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
            try {
                client.wss(echoWebsocket) {
                    outgoing.send(Frame.Text("Hello"))
                    val frame = incoming.receive()
                    check(frame is Frame.Text)

                    throw CustomException()
                }
            } catch (cause: Throwable) {
                if (cause !is CustomException) {
                    error("Expected ${CustomException::class} exception, but $cause was thrown instead")
                }
                return@test
            }
            error("Expected ${CustomException::class} exception, but it wasn't thrown")
        }
    }
}
