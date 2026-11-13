/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.kron

import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WebSocketTest {
    private lateinit var server: TestServer

    @BeforeTest
    fun start() {
        server = TestServer {
            install(WebSockets)
            routing {
                webSocket("/ws") {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            send(Frame.Text("echo:" + frame.readText()))
                        }
                    }
                }
            }
        }
    }

    @AfterTest
    fun stop() {
        server.close()
    }

    @Test
    fun `a WebSocket upgrade echoes text frames both ways until the client closes`() = server.connect().use { client ->
        client.write(
            "GET /ws HTTP/1.1\r\nHost: x\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n",
        )
        val upgrade = client.readResponse(head = true)
        assertEquals(101, upgrade.status)
        assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", upgrade.header("Sec-WebSocket-Accept"))

        for (text in listOf("hi", "second", "x".repeat(300))) {
            client.write(clientFrame(0x1, text.encodeToByteArray()))
            assertEquals("echo:$text", readTextFrame(client))
        }

        client.write(clientFrame(0x8, byteArrayOf(0x03, 0xE8.toByte()))) // close, 1000
        val close = client.readFrame()
        assertEquals(0x8, close.first)
    }

    // A masked client frame, FIN set.
    private fun clientFrame(opcode: Int, payload: ByteArray): ByteArray {
        val mask = byteArrayOf(1, 2, 3, 4)
        val head = if (payload.size < 126) {
            byteArrayOf((0x80 or opcode).toByte(), (0x80 or payload.size).toByte())
        } else {
            byteArrayOf(
                (0x80 or opcode).toByte(),
                (0x80 or 126).toByte(),
                (payload.size ushr 8).toByte(),
                payload.size.toByte()
            )
        }
        return head + mask + ByteArray(payload.size) { (payload[it].toInt() xor mask[it % 4].toInt()).toByte() }
    }

    private fun readTextFrame(client: TestClient): String {
        val (opcode, payload) = client.readFrame()
        assertEquals(0x1, opcode)
        return payload.decodeToString()
    }

    // An unmasked server frame: its opcode and payload.
    private fun TestClient.readFrame(): Pair<Int, ByteArray> {
        val head = readBytes(2)
        var length = head[1].toInt() and 0x7F
        if (length == 126) {
            val ext = readBytes(2)
            length = ((ext[0].toInt() and 0xFF) shl 8) or (ext[1].toInt() and 0xFF)
        }
        return (head[0].toInt() and 0x0F) to readBytes(length)
    }
}
