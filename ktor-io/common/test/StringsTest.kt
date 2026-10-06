/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.io.EOFException
import kotlin.test.*

class StringsTest {

    @Test
    fun `toByteArray uses non utf8 encoder when charset differs`() {
        val text = "AéB"

        val bytes = text.toByteArray(Charsets.ISO_8859_1)

        assertContentEquals(byteArrayOf(0x41, 0xE9.toByte(), 0x42), bytes)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `deprecated String constructor decodes utf8 byte range`() {
        val bytes = "prefix-é-middle-suffix".encodeToByteArray()

        val text = String(bytes, offset = 7, length = 9, charset = Charsets.UTF_8)

        assertEquals("é-middle", text)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `deprecated String constructor decodes non utf8 bytes`() {
        val bytes = byteArrayOf(0x41, 0xE9.toByte(), 0x42)

        val text = String(bytes, charset = Charsets.ISO_8859_1)

        assertEquals("AéB", text)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `readBytes reads all remaining bytes`() {
        val packet = buildPacket {
            writeFully(byteArrayOf(1, 2, 3, 4))
        }

        assertContentEquals(byteArrayOf(1, 2, 3, 4), packet.readBytes())
    }

    @Suppress("DEPRECATION")
    @Test
    fun `readBytes count reads exact amount and leaves rest`() {
        val packet = buildPacket {
            writeFully(byteArrayOf(1, 2, 3, 4))
        }

        assertContentEquals(byteArrayOf(1, 2), packet.readBytes(2))
        assertContentEquals(byteArrayOf(3, 4), packet.readBytes())
    }

    @Suppress("DEPRECATION")
    @Test
    fun `readTextExact delegates to readTextExactCharacters`() {
        val packet = buildPacket {
            writeText("abcdef")
        }

        assertEquals("abcd", packet.readTextExact(n = 4))
    }

    @Test
    fun `readTextExactCharacters returns requested count with utf8`() {
        val packet = buildPacket {
            writeText("éßabc")
        }

        assertEquals("éß", packet.readTextExactCharacters(2))
    }

    @Test
    fun `readTextExactCharacters with ISO-8859-1 leaves trailing bytes`() {
        val packet = buildPacket {
            writeFully(byteArrayOf(0x41, 0xE9.toByte(), 0x42, 0x43))
        }

        assertEquals("Aé", packet.readTextExactCharacters(2, Charsets.ISO_8859_1))
        assertEquals("BC", packet.readText(Charsets.ISO_8859_1))
    }

    @Test
    fun `readTextExactCharacters throws on premature eof`() {
        val packet = buildPacket {
            writeText("hi")
        }

        assertFailsWith<EOFException> {
            packet.readTextExactCharacters(3)
        }
    }

    @Test
    fun `readTextExactCharacters with zero count returns empty and keeps source intact`() {
        val packet = buildPacket {
            writeText("abc")
        }

        assertEquals("", packet.readTextExactCharacters(0))
        assertEquals("abc", packet.readText())
    }

    @Test
    fun `readTextExactCharacters rejects negative count`() {
        val packet = buildPacket {
            writeText("abc")
        }

        val cause = assertFailsWith<IllegalArgumentException> {
            packet.readTextExactCharacters(-1)
        }
        assertContains(cause.message.orEmpty(), "shouldn't be negative")
    }

    @Test
    fun `readTextExactCharacters supports single-byte charset decoding`() {
        val charsetName = when {
            Charsets.isSupported("windows-1252") -> "windows-1252"
            Charsets.isSupported("US-ASCII") -> "US-ASCII"
            else -> return
        }
        val charset = Charsets.forName(charsetName)

        val packet = buildPacket {
            writeText("ABCZ", charset = charset)
        }

        assertEquals("ABC", packet.readTextExactCharacters(3, charset))
        assertEquals("Z", packet.readText(charset))
    }

    @Test
    fun `readTextExactCharacters rejects unsupported charset`() {
        if (!Charsets.isSupported("UTF-16")) return

        val packet = buildPacket {
            writeText("abc")
        }

        val cause = assertFailsWith<IllegalArgumentException> {
            packet.readTextExactCharacters(1, Charsets.forName("UTF-16"))
        }
        assertContains(cause.message.orEmpty(), "Unsupported charset")
    }

    @Test
    fun `readTextExactCharacters utf8 does not overconsume source`() {
        val packet = buildPacket {
            writeText("eéX")
        }

        assertEquals("eé", packet.readTextExactCharacters(2, Charsets.UTF_8))
        assertEquals("X", packet.readText(Charsets.UTF_8))
    }

    @Test
    fun `readTextExactCharacters utf8 reads supplementary code point for two utf16 units`() {
        val packet = buildPacket {
            writeText("😀X")
        }

        assertEquals("😀", packet.readTextExactCharacters(2, Charsets.UTF_8))
        assertEquals("X", packet.readText(Charsets.UTF_8))
    }

    @Test
    fun `readTextExactCharacters utf8 fails when next code point exceeds requested utf16 units`() {
        val packet = buildPacket {
            writeText("😀X")
        }

        assertFailsWith<MalformedInputException> {
            packet.readTextExactCharacters(1, Charsets.UTF_8)
        }
        assertEquals("😀X", packet.readText(Charsets.UTF_8))
    }

    @Test
    fun `readTextExactCharacters utf8 fails on invalid leading byte`() {
        val packet = buildPacket {
            writeFully(byteArrayOf(0x80.toByte()))
        }

        assertFailsWith<MalformedInputException> {
            packet.readTextExactCharacters(1, Charsets.UTF_8)
        }
    }

    @Test
    fun `readTextExactCharacters utf8 fails on malformed four-byte out-of-range code point`() {
        val packet = buildPacket {
            writeFully(byteArrayOf(0xF4.toByte(), 0x90.toByte(), 0x80.toByte(), 0x80.toByte()))
        }

        assertFailsWith<MalformedInputException> {
            packet.readTextExactCharacters(2, Charsets.UTF_8)
        }
    }

    @Test
    fun `writeText CharSequence writes selected utf8 range`() {
        val packet = buildPacket {
            writeText("prefix-middle-suffix", fromIndex = 7, toIndex = 13)
        }

        assertEquals("middle", packet.readText())
    }

    @Test
    fun `writeText CharSequence writes selected range with non utf8 charset`() {
        val packet = buildPacket {
            writeText("AéBÇ", fromIndex = 1, toIndex = 3, charset = Charsets.ISO_8859_1)
        }

        assertEquals("éB", packet.readText(Charsets.ISO_8859_1))
    }

    @Test
    fun `writeText CharArray writes selected utf8 range`() {
        val packet = buildPacket {
            writeText(charArrayOf('a', 'b', 'c', 'd', 'e'), fromIndex = 1, toIndex = 4)
        }

        assertEquals("bcd", packet.readText())
    }

    @Test
    fun `writeText CharArray writes selected range with non utf8 charset`() {
        val packet = buildPacket {
            writeText(charArrayOf('A', 'é', 'B', 'Ç'), fromIndex = 1, toIndex = 3, charset = Charsets.ISO_8859_1)
        }

        assertEquals("éB", packet.readText(Charsets.ISO_8859_1))
    }
}
