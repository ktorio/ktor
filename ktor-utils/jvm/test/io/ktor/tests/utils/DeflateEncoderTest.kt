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
import kotlin.test.*

class DeflateEncoderTest {

    @Test
    fun `decode handles zlib-wrapped deflate`() = runBlocking {
        val original = "Hello, zlib-wrapped deflate!".toByteArray(Charsets.UTF_8)
        val compressed = ByteArrayOutputStream().also { baos ->
            DeflaterOutputStream(baos).use { it.write(original) }
        }.toByteArray()

        val decoded = Deflate.decode(ByteReadChannel(compressed))
            .readRemaining().readByteArray()

        assertContentEquals(original, decoded)
    }

    @Test
    fun `decode handles raw deflate`() = runBlocking {
        val original = "Hello, raw deflate!".toByteArray(Charsets.UTF_8)
        val compressed = ByteArrayOutputStream().also { baos ->
            DeflaterOutputStream(baos, Deflater(Deflater.DEFAULT_COMPRESSION, true)).use { it.write(original) }
        }.toByteArray()

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
}
