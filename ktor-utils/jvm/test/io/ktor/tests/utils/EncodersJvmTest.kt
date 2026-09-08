/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.utils

import io.ktor.test.*
import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.time.Duration.Companion.seconds

class EncodersJvmTest {

    @Test
    fun `malformed gzip fails`() = runTest(timeout = 1.seconds) {
        @Suppress("ktlint:standard:no-multi-spaces")
        val malformedGzip = byteArrayOf(
            0x1f, 0x8b.toByte(),    // Magic
            0x08,                   // Deflate method
            0x00,                   // Flags
            0x00, 0x00, 0x00, 0x00, // Timestamp
            0x00,                   // Extra flags
            0xff.toByte(),          // OS
            // Incomplete deflate stream
            0x01, 0x00, 0x00,
        )

        val failure = assertFails("Malformed gzip should fail") { GZip.decodeBytes(malformedGzip) }
        assertEquals("Compressed input is incomplete.", failure.message)
    }

    @Test
    fun `deflate with full output buffer`() = runTest(timeout = 1.seconds) {
        val bytes = ByteArray(DEFAULT_BUFFER_SIZE + 1) { 1 }
        val compressed = Deflate.encodeBytes(bytes)
        val decoded = Deflate.decodeBytes(compressed)

        assertContentEquals(bytes, decoded)
    }
}

private suspend fun Encoder.encodeBytes(bytes: ByteArray): ByteArray =
    encode(ByteReadChannel(bytes), currentCoroutineContext()).readBuffer().readByteArray()

private suspend fun Encoder.decodeBytes(bytes: ByteArray): ByteArray =
    decode(ByteReadChannel(bytes), currentCoroutineContext()).readBuffer().readByteArray()
