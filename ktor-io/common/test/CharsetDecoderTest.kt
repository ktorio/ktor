/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// ABOUTME: Tests for CharsetDecoder.decode() to verify correct handling of
// ABOUTME: malformed and truncated byte sequences across all platforms.

import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.io.*
import kotlin.test.*

class CharsetDecoderTest {

    @Test
    fun `KTOR-9372 decoding truncated UTF-8 should not cause infinite loop`() {
        // Incomplete 2-byte UTF-8 sequence: 0xC2 expects a continuation byte (0x80-0xBF)
        val truncated = byteArrayOf(0xC2.toByte())
        val source = buildPacket { writeFully(truncated) }

        // Must complete without hanging. On native (iconv) platforms, this should throw
        // MalformedInputException. On JVM, it may return a string with replacement characters.
        try {
            Charsets.UTF_8.newDecoder().decode(source)
        } catch (_: MalformedInputException) {
            // Expected on native platforms where iconv detects incomplete sequences
        }
    }

    @Test
    fun `KTOR-9372 decoding truncated 3-byte UTF-8 should not cause infinite loop`() {
        // Incomplete 3-byte UTF-8 sequence: 0xE0 0xA0 expects one more continuation byte
        val truncated = byteArrayOf(0xE0.toByte(), 0xA0.toByte())
        val source = buildPacket { writeFully(truncated) }

        try {
            Charsets.UTF_8.newDecoder().decode(source)
        } catch (_: MalformedInputException) {
            // Expected on native platforms where iconv detects incomplete sequences
        }
    }

    @Test
    fun `KTOR-9372 decoding truncated 4-byte UTF-8 should not cause infinite loop`() {
        // Incomplete 4-byte UTF-8 sequence: 0xF0 0x90 0x80 expects one more continuation byte
        val truncated = byteArrayOf(0xF0.toByte(), 0x90.toByte(), 0x80.toByte())
        val source = buildPacket { writeFully(truncated) }

        try {
            Charsets.UTF_8.newDecoder().decode(source)
        } catch (_: MalformedInputException) {
            // Expected on native platforms where iconv detects incomplete sequences
        }
    }

    @Test
    fun `KTOR-9372 decoding valid UTF-8 followed by truncated sequence should not hang`() {
        // Valid "Hello" followed by an incomplete 2-byte sequence
        val data = byteArrayOf(
            0x48,
            0x65,
            0x6C,
            0x6C,
            0x6F, // "Hello"
            0xC2.toByte() // incomplete 2-byte sequence
        )
        val source = buildPacket { writeFully(data) }

        try {
            val result = Charsets.UTF_8.newDecoder().decode(source)
            // On JVM, the valid prefix should be decoded
            assertTrue(result.startsWith("Hello"))
        } catch (_: MalformedInputException) {
            // Expected on native platforms
        }
    }
}
