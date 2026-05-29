/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.encoding.zstd

import io.ktor.encoding.zstd.ZstdEncoder.Companion.DEFAULT_COMPRESSION_LEVEL
import io.ktor.test.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.pool.*
import kotlinx.io.readByteArray
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Taken from [KtorDefaultPool]
 */
private const val DEFAULT_BUFFER_SIZE = 4098

class ZstdTest {

    @Test
    fun `encode handles incompressible payload close to buffer size`() = runTest {
        val bytes = Random(0).nextBytes(DEFAULT_BUFFER_SIZE)

        val encodedReadChannel = encodeBytes(bytes)
        val decodedReadChannel = ZstdEncoder().decode(encodedReadChannel)
        val decodedBytes = decodedReadChannel.readRemaining().readByteArray()

        assertContentEquals(bytes, decodedBytes)
    }

    @Test
    fun `decode handles payload split into multiple zstd frames`() = runTest {
        val firstPart = "zstd".repeat(DEFAULT_BUFFER_SIZE)
        val secondPart = "ktor".repeat(DEFAULT_BUFFER_SIZE)
        val string = firstPart + secondPart

        val firstFrame = encodeString(firstPart).readRemaining().readByteArray()
        val secondFrame = encodeString(secondPart).readRemaining().readByteArray()

        val encodedReadChannel = ByteReadChannel(firstFrame + secondFrame)
        val decodedReadChannel = ZstdEncoder().decode(encodedReadChannel)
        val decodedString = decodedReadChannel.readRemaining().readText()

        assertEquals(string, decodedString)
    }

    @Test
    fun `decode handles zstd frames that span multiple buffer reads`() = runTest {
        val string = "zstd".repeat(DEFAULT_BUFFER_SIZE)

        val encodedReadChannel = encodeString(string)
        val decodedReadChannel = ByteChannel()
        with(Zstd(DEFAULT_COMPRESSION_LEVEL)) {
            // the absolute smallest possible zstd frame is 6 bytes,
            // so use 5 bytes to be sure we're smaller than that
            encodedReadChannel.decodeTo(decodedReadChannel, DirectByteBufferPool(capacity = 10, bufferSize = 5))
        }
        decodedReadChannel.close()
        val decodedString = decodedReadChannel.readRemaining().readText()

        assertEquals(string, decodedString)
    }
}

private fun encodeString(string: String): ByteReadChannel = encodeBytes(string.toByteArray())
private fun encodeBytes(bytes: ByteArray): ByteReadChannel = ZstdEncoder().encode(ByteReadChannel(bytes))
