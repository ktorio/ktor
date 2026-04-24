/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.utils

import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.io.readByteArray
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream
import kotlin.random.Random
import kotlin.test.*

class DeflateEncoderTest {

    @Test
    fun `decode handles zlib-wrapped deflate`() = runBlocking {
        val original = "Hello, zlib-wrapped deflate!".toByteArray(Charsets.UTF_8)
        val compressed = deflateWithZibWrapping(original)

        val decoded = Deflate.decode(ByteReadChannel(compressed))
            .readRemaining().readByteArray()

        assertContentEquals(original, decoded)
    }

    @Test
    fun `decode handles raw deflate`() = runBlocking {
        val original = "Hello, raw deflate!".toByteArray(Charsets.UTF_8)
        val compressed = deflateRaw(original)

        val decoded = Deflate.decode(ByteReadChannel(compressed))
            .readRemaining().readByteArray()

        assertContentEquals(original, decoded)
    }

    @Test
    fun `encode produces zlib-wrapped deflate`() = runBlocking {
        val original = "Hello, encode deflate!".toByteArray(Charsets.UTF_8)
        val encoded = Deflate.encode(ByteReadChannel(original))
            .readRemaining().readByteArray()

        val decoded = InflaterInputStream(encoded.inputStream()).readBytes()

        assertContentEquals(original, decoded)
    }

    @Test
    fun `encode and decode roundtrip`() = runBlocking {
        val original = "Round-trip deflate test payload!".toByteArray(Charsets.UTF_8)
        val encoded = Deflate.encode(ByteReadChannel(original))
        val decoded = Deflate.decode(encoded).readRemaining().readByteArray()

        assertContentEquals(original, decoded)
    }

    @Test
    fun `decode correctly identifies format for 100000 random payloads`() = runBlocking {
        val random = Random(seed = 42)
        repeat(10_000) { iteration ->
            val original = random.nextBytes(random.nextInt(0, 128))

            val zlibCompressed = deflateWithZibWrapping(original)
            assertContentEquals(
                original,
                Deflate.decode(ByteReadChannel(zlibCompressed)).readRemaining().readByteArray(),
                "zlib decode failed at iteration $iteration"
            )

            val rawCompressed = deflateRaw(original)
            assertContentEquals(
                original,
                Deflate.decode(ByteReadChannel(rawCompressed)).readRemaining().readByteArray(),
                "raw decode failed at iteration $iteration"
            )
        }
    }

    private fun deflateWithZibWrapping(data: ByteArray): ByteArray = ByteArrayOutputStream().also { baos ->
        DeflaterOutputStream(baos).use { it.write(data) }
    }.toByteArray()

    private fun deflateRaw(data: ByteArray): ByteArray = ByteArrayOutputStream().also { baos ->
        DeflaterOutputStream(baos, Deflater(Deflater.DEFAULT_COMPRESSION,true)).use { it.write(data) }
    }.toByteArray()
}
