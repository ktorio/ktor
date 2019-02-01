package io.ktor.client.features.websocket

import io.ktor.application.*
import io.ktor.client.engine.cio.*
import io.ktor.client.tests.utils.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.websocket.*
import kotlin.test.*

class WebSocketRawTest : TestWithKtor() {
    override val server: ApplicationEngine = embeddedServer(Netty, serverPort) {
        install(io.ktor.websocket.WebSockets)
        routing {
            webSocketRaw("/rawEcho") {
                for (frame in incoming) {
                    if (frame is Frame.Close) {
                        outgoing.send(Frame.Close())
                        break
                    }

                    outgoing.send(frame)
                }
            }
        }
    }

    @Test
    fun testPingPongRaw() = clientTest(CIO) {
        config {
            install(WebSockets)
        }

        test { client ->
            client.wsRaw(port = serverPort, path = "rawEcho") {
                repeat(10) {
                    outgoing.send(Frame.Text("text: $it"))

                    val frame = incoming.receive()
                    assert(frame is Frame.Text)
                    assertEquals("text: $it", (frame as Frame.Text).readText())

                }

                outgoing.send(Frame.Close())
            }
        }
    }

    @Test
    fun testConvenienceMethods() = clientTest(CIO) {
        config {
            install(WebSockets)
        }

        test { client ->
            client.wsRaw(port = serverPort, path = "rawEcho") {

                run {
                    val message = "my text message"
                    send(message)
                    val frame = incoming.receive()
                    assert(frame is Frame.Text)
                    assertEquals(message, (frame as Frame.Text).readText())
                }

                run {
                    val message = byteArrayOf(1, 2, 3, 4)
                    send(message)
                    val frame = incoming.receive()
                    assert(frame is Frame.Binary)
                    assertEquals(message.toList(), frame.readBytes().toList())
                }

                outgoing.send(Frame.Close())
            }
        }
    }
}
