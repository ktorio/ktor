package io.ktor.utils.io.tests

import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlin.test.*

private const val TEXT = "test\u00f0."
private val BYTES = byteArrayOf(0x74, 0x65, 0x73, 0x74, 0xf0.toByte(), 0x2e)

class ISOTest {
    @Test
    fun testEncode() {
        val bytes = Charsets.ISO_8859_1.newEncoder().encode(TEXT).readBytes()
        assertTrue {
            bytes.contentEquals(BYTES)
        }
    }

    @Test
    fun testEncodeUnmappable() {
        assertFailsWith<MalformedInputException> {
            Charsets.ISO_8859_1.newEncoder().encode("\u0422")
        }
    }

    @Test
    fun testDecode() {
        val pkt = ByteReadPacket(BYTES)
        val result = Charsets.ISO_8859_1.newDecoder().decode(pkt)
        assertEquals(TEXT, result)
    }
}
