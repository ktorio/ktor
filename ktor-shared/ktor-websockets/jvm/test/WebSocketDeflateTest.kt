/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.websocket

import io.ktor.websocket.*
import io.ktor.websocket.internals.*
import java.util.zip.*
import kotlin.random.*
import kotlin.test.*

class WebSocketDeflateTest {
    private val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
    private val inflater = Inflater(true)

    private val config = WebSocketDeflateExtension.Config()
    private val extension = WebSocketDeflateExtension(config)

    @Test
    fun testDeflateInflateEmpty() {
        val data = byteArrayOf()
        val deflated = deflater.deflateFully(data)
        val inflated = inflater.inflateFully(deflated)

        assertTrue { data.contentEquals(inflated) }
    }

    @Test
    fun testDeflateInflateForRandomData() {
        repeat(1000) {
            val data = Random.nextBytes(it * 10)
            val deflated = deflater.deflateFully(data)
            val inflated = inflater.inflateFully(deflated)

            assertTrue {
                data.contentEquals(inflated)
            }
        }
    }

    @Test
    fun testClientAcceptsServerNoContextTakeover() {
        val negotiatedProtocols = listOf(
            WebSocketExtensionHeader("permessage-deflate", listOf("server_no_context_takeover"))
        )
        extension.clientNegotiation(negotiatedProtocols)

        assertEquals(extension.incomingNoContextTakeover, true)
        assertEquals(extension.outgoingNoContextTakeover, false)
    }

    @Test
    fun testClientAcceptsClientNoContextTakeover() {
        val negotiatedProtocols = listOf(
            WebSocketExtensionHeader("permessage-deflate", listOf("client_no_context_takeover"))
        )
        extension.clientNegotiation(negotiatedProtocols)

        assertEquals(extension.incomingNoContextTakeover, false)
        assertEquals(extension.outgoingNoContextTakeover, true)
    }

    @Test
    fun testManualConfig() {
        val config = WebSocketDeflateExtension.Config()
        config.manualConfig(mutableListOf())

        config.configureProtocols {
            it.add(WebSocketExtensionHeader("permessage-deflate", listOf("client_no_context_takeover")))
        }

        config.manualConfig(mutableListOf())
    }

    @Test
    fun testDecompressAllFrames() {
        val chunk1 = "Hello, ".toByteArray()
        val chunk2 = "World!".toByteArray()

        val frame1 = extension.processOutgoingFrame(Frame.Binary(false, chunk1))
        val frame2Temp = extension.processOutgoingFrame(Frame.Binary(true, chunk2))
        val frame2 = Frame.Binary(true, frame2Temp.data, rsv1 = false)

        val decoded1 = extension.processIncomingFrame(frame1) as Frame.Binary
        val decoded2 = extension.processIncomingFrame(frame2) as Frame.Binary

        val message = decoded1.data + decoded2.data
        val actual = String(message)
        val expected = "Hello, World!"
        assertEquals(expected, actual)
    }
}
