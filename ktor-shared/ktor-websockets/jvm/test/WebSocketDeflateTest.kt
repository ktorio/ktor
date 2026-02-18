/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.websocket

import io.ktor.websocket.*
import io.ktor.websocket.internals.*
import kotlinx.io.IOException
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlin.random.Random
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

    @Test
    fun `prevents infinite loop`() {
        val dict = ByteArray(64) { 7 }
        val original = ByteArray(1024) { 1 }

        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, false).apply {
            setDictionary(dict)
            setInput(original)
            finish()
        }

        val buf = ByteArray(4096)
        val n = deflater.deflate(buf)
        val dictCompressed = buf.copyOf(n)

        val payload = dictCompressed + ByteArray(64) { 0x13 }
        val inflater = Inflater(false)

        val error = assertFailsWith<IOException> {
            inflater.inflateFully(payload)
        }.message

        assertNotNull(error)
        assertContains(
            error,
            "Inflater needs a preset dictionary",
            message = "Expected zero spin failure, got: $error"
        )
    }

    @Test
    fun `checks for max inflate size`() {
        val bombLikePlaintext = ByteArray(32 * 1024 * 1024) { 0 } // highly compressible, big after inflate

        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, false)
        val compressed = deflater.deflateFully(bombLikePlaintext)

        val inflater = Inflater(false)
        val error = assertFailsWith<IOException> {
            inflater.inflateFully(compressed, maxOutputSize = 1 * 1024 * 1024) // 1 MiB cap
        }.message

        assertNotNull(error)
        assertContains(
            error,
            "Inflated data exceeds limit",
            message = "Expected size limit failure, got: $error"
        )
    }
}
