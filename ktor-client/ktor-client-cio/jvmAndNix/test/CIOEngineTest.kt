/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.tests.utils.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlin.test.*

class CIOEngineTest {

    @Test
    fun testRequestTimeoutIgnoredWithWebSocket(): Unit = runBlocking {
        val client = HttpClient(CIO) {
            engine {
                requestTimeout = 10
            }

            install(WebSockets)
        }

        var received = false
        client.ws("$TEST_WEBSOCKET_SERVER/websockets/echo") {
            delay(20)

            send(Frame.Text("Hello"))

            val response = incoming.receive() as Frame.Text
            received = true
            assertEquals("Hello", response.readText())
        }

        assertTrue(received)
    }
}
