/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlin.test.*

class CharsetUtf16Test {

    @Test
    fun `decoding UTF-16 with BE BOM produces correct text`() {
        val bytes = byteArrayOf(
            0xFE.toByte(), 0xFF.toByte(),
            0x00, 0x48,
            0x00, 0x65,
            0x00, 0x6C,
            0x00, 0x6C,
            0x00, 0x6F,
            0x00, 0x2C,
            0x00, 0x20,
            0x00, 0x57,
            0x00, 0x6F,
            0x00, 0x72,
            0x00, 0x6C,
            0x00, 0x64,
            0x00, 0x21,
        )
        val source = buildPacket { writeFully(bytes) }

        val text = Charsets.forName("UTF-16").newDecoder().decode(source)

        assertEquals("Hello, World!", text)
    }

    @Test
    fun `decoding UTF-16 with LE BOM produces correct text`() {
        val bytes = byteArrayOf(
            0xFF.toByte(), 0xFE.toByte(),
            0x48, 0x00,
            0x65, 0x00,
            0x6C, 0x00,
            0x6C, 0x00,
            0x6F, 0x00,
            0x2C, 0x00,
            0x20, 0x00,
            0x57, 0x00,
            0x6F, 0x00,
            0x72, 0x00,
            0x6C, 0x00,
            0x64, 0x00,
            0x21, 0x00,
        )
        val source = buildPacket { writeFully(bytes) }

        val text = Charsets.forName("UTF-16").newDecoder().decode(source)

        assertEquals("Hello, World!", text)
    }

    @Test
    fun `UTF-16BE and UTF-16LE are supported`() {
        assertTrue(Charsets.isSupported("UTF-16BE"))
        assertTrue(Charsets.isSupported("UTF-16LE"))
    }

    @Test
    fun `explicit UTF-16LE decodes without BOM`() {
        val bytes = byteArrayOf(
            0x48,
            0x00,
            0x69,
            0x00,
        )
        val source = buildPacket { writeFully(bytes) }

        val text = Charsets.forName("UTF-16LE").newDecoder().decode(source)

        assertEquals("Hi", text)
    }

    @Test
    fun `unsupported charset`() {
        assertFalse(Charsets.isSupported("NOT-A-CHARSET"))
        assertFailsWith<IllegalArgumentException> {
            Charsets.forName("NOT-A-CHARSET")
        }
    }

    @Test
    fun `UTF-16LE does not decode big-endian bytes correctly`() {
        val beBytes = byteArrayOf(0x00, 0x48, 0x00, 0x69)
        val source = buildPacket { writeFully(beBytes) }

        val text = Charsets.forName("UTF-16LE").newDecoder().decode(source)

        assertNotEquals("Hi", text)
    }

    @Test
    fun `decoding truncated UTF-16 should not hang`() {
        val truncated = byteArrayOf(0x00)
        val source = buildPacket { writeFully(truncated) }
        val result = runCatching { Charsets.forName("UTF-16").newDecoder().decode(source) }
        assertDecodeFailed(result)
    }

    @Test
    fun `decoding truncated UTF-16LE should not hang`() {
        val truncated = byteArrayOf(0x48)
        val source = buildPacket { writeFully(truncated) }
        val result = runCatching { Charsets.forName("UTF-16LE").newDecoder().decode(source) }
        assertDecodeFailed(result)
    }
}

private fun assertDecodeFailed(result: Result<String>) {
    when {
        result.isFailure && result.exceptionOrNull() is MalformedInputException -> Unit
        result.isSuccess && result.getOrNull() == "\uFFFD" -> Unit
        result.isSuccess -> fail("Expected decode to fail but got: ${result.getOrNull()}")
        else -> fail("Unexpected failure: ${result.exceptionOrNull()}")
    }
}
