package io.ktor.tests.websocket

import io.ktor.http.cio.websocket.*
import io.ktor.util.*
import org.junit.Test
import java.nio.*
import kotlin.test.*

@UseExperimental(WebSocketInternalAPI::class)
class ParserTest {
    @Test
    fun testParserSimpleFrame() {
        val buffer = bufferOf("0x81 0x05 0x48 0x65 0x6c 0x6c 0x6f")
        val parser = FrameParser()
        parser.frame(buffer)

        assertEquals(FrameType.TEXT, parser.frameType)
        assertTrue { parser.fin }
        assertFalse { parser.mask }
        assertEquals(5, parser.length)
        assertTrue { parser.bodyReady }

        assertEquals("Hello", buffer.decodeString())
    }

    @Test
    fun testParserU16Frame() {
        val buffer = bufferOf("0x81 0x7e 0x00 0x06")
        val parser = FrameParser()
        parser.frame(buffer)

        assertEquals(FrameType.TEXT, parser.frameType)
        assertTrue { parser.fin }
        assertFalse { parser.mask }
        assertEquals(6, parser.length)
        assertTrue { parser.bodyReady }
    }

    @Test
    fun testParserU64Frame() {
        val buffer = bufferOf("0x81 0x7f 0x12 0x34 0x56 0x78 0x9a 0xab 0xcd 0xef")
        val parser = FrameParser()
        parser.frame(buffer)

        assertEquals(FrameType.TEXT, parser.frameType)
        assertTrue { parser.fin }
        assertFalse { parser.mask }
        assertEquals(0x123456789aabcdef, parser.length)
        assertTrue { parser.bodyReady }
    }

    @Test
    fun testParserMasking() {
        val buffer = bufferOf("0x81 0x85 0x37 0xfa 0x21 0x3d 0x7f 0x9f 0x4d 0x51 0x58")
        val parser = FrameParser()
        parser.frame(buffer)

        assertEquals(FrameType.TEXT, parser.frameType)
        assertTrue { parser.fin }
        assertTrue { parser.mask }
        assertEquals(5, parser.length)
        assertTrue { parser.bodyReady }
        assertNotNull(parser.maskKey)
    }

    @Test
    fun testParserFragmentation() {
        val buffer = bufferOf("0x01 0x01 0x31 0x00 0x01 0x32 0x80 0x01 0x33")
        val parser = FrameParser()
        parser.frame(buffer)

        assertEquals(FrameType.TEXT, parser.frameType)
        assertFalse { parser.fin }
        assertFalse { parser.mask }
        assertEquals(1, parser.length)
        assertTrue { parser.bodyReady }

        assertEquals('1', buffer.get().toChar())
        parser.bodyComplete()

        parser.frame(buffer)

        assertEquals(FrameType.TEXT, parser.frameType)
        assertFalse { parser.fin }
        assertFalse { parser.mask }
        assertEquals(1, parser.length)
        assertTrue { parser.bodyReady }

        assertEquals('2', buffer.get().toChar())
        parser.bodyComplete()

        parser.frame(buffer)

        assertEquals(FrameType.TEXT, parser.frameType)
        assertTrue { parser.fin }
        assertFalse { parser.mask }
        assertEquals(1, parser.length)
        assertTrue { parser.bodyReady }

        assertEquals('3', buffer.get().toChar())
        parser.bodyComplete()

        assertFalse { buffer.hasRemaining() }
    }

    private fun String.trimHex() = replace("\\s+".toRegex(), "").replace("0x", "")
    private fun bufferOf(hex: String) = ByteBuffer.wrap(hex(hex.trimHex()))
}