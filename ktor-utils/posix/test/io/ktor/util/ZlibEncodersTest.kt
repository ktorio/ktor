/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import io.ktor.test.*
import io.ktor.utils.io.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.test.*

class ZlibEncodersTest {
    private val text = "Ktor on Kotlin/Native compresses with zlib. ".repeat(4).encodeToByteArray()

    // What Python's gzip and zlib make of text (gzip at level 9 with no mtime, deflate as a raw stream): an encoder
    // other than this one, so decoding them checks the format rather than a round trip.
    private val pythonGzip = bytes(
        0x1f, 0x8b, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02, 0x03, 0xf3, 0x2e, 0xc9, 0x2f, 0x52, 0xc8, 0xcf, 0x53,
        0xf0, 0xce, 0x2f, 0xc9, 0xc9, 0xcc, 0xd3, 0xf7, 0x4b, 0x2c, 0xc9, 0x2c, 0x4b, 0x55, 0x48, 0xce, 0xcf, 0x2d,
        0x28, 0x4a, 0x2d, 0x2e, 0x4e, 0x2d, 0x56, 0x28, 0xcf, 0x2c, 0xc9, 0x50, 0xa8, 0xca, 0xc9, 0x4c, 0xd2, 0x53,
        0xf0, 0x1e, 0x04, 0x6a, 0x01, 0x56, 0xf2, 0x56, 0x33, 0xb0, 0x00, 0x00, 0x00
    )
    private val pythonDeflate = pythonGzip.copyOfRange(10, pythonGzip.size - 8)

    @Test
    fun gzipRoundTripsEverySize() = runTest {
        for (size in listOf(0, 1, 1000, 100_000, 1_000_000)) {
            val original = sample(size)
            assertContentEquals(original, decode(GZipEncoder, encode(GZipEncoder, original)), "size $size")
        }
    }

    @Test
    fun deflateRoundTripsEverySize() = runTest {
        for (size in listOf(0, 1, 1000, 100_000, 1_000_000)) {
            val original = sample(size)
            assertContentEquals(original, decode(DeflateEncoder, encode(DeflateEncoder, original)), "size $size")
        }
    }

    @Test
    fun gzipIsTheGzipFormatAndSmaller() = runTest {
        val encoded = encode(GZipEncoder, text)
        assertEquals(0x1f, encoded[0].toInt() and 0xff)
        assertEquals(0x8b, encoded[1].toInt() and 0xff)
        assertEquals(8, encoded[2].toInt(), "compression method: deflate")
        assertTrue(encoded.size < text.size, "${encoded.size} bytes for ${text.size}")
    }

    @Test
    fun decodesWhatAnotherEncoderMade() = runTest {
        assertContentEquals(text, decode(GZipEncoder, pythonGzip))
        assertContentEquals(text, decode(DeflateEncoder, pythonDeflate))
    }

    @Test
    fun aCorruptGzipFailsToDecode() = runTest {
        val corrupt = pythonGzip.copyOf()
        corrupt[corrupt.size - 6] = (corrupt[corrupt.size - 6] + 1).toByte() // the CRC
        assertFails { decode(GZipEncoder, corrupt) }
    }

    @Test
    fun aTruncatedGzipFailsToDecode() = runTest {
        assertFails { decode(GZipEncoder, pythonGzip.copyOf(pythonGzip.size - 4)) }
    }

    @Test
    fun encodesThroughAWriteChannel() = runTest {
        val original = sample(100_000)
        val compressed = ByteChannel(autoFlush = true)
        coroutineScope {
            launch {
                val writer = GZipEncoder.encode(compressed as ByteWriteChannel, coroutineContext)
                writer.writeFully(original)
                writer.flushAndClose()
            }

            // As on the JVM, the encoder leaves its destination open: the decoder stops where the stream ends
            assertContentEquals(original, GZipEncoder.decode(compressed, coroutineContext).toByteArray())
        }
    }

    private suspend fun encode(encoder: ContentEncoder, bytes: ByteArray): ByteArray = coroutineScope {
        encoder.encode(ByteReadChannel(bytes), coroutineContext).toByteArray()
    }

    private suspend fun decode(encoder: ContentEncoder, bytes: ByteArray): ByteArray = coroutineScope {
        encoder.decode(ByteReadChannel(bytes), coroutineContext).toByteArray()
    }

    // Half random, half repetitive: both what compresses and what does not
    private fun sample(size: Int): ByteArray {
        val random = Random(size)
        return ByteArray(size) { i -> if (i % 2 == 0) random.nextInt().toByte() else (i % 7).toByte() }
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
