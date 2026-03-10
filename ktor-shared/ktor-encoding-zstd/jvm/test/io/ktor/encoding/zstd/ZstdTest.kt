/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.encoding.zstd

import io.ktor.encoding.zstd.ZstdEncoder.Companion.DEFAULT_COMPRESSION_LEVEL
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.pool.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Taken from [KtorDefaultPool]
 */
private const val DEFAULT_BUFFER_SIZE = 4098

class ZstdTest {

    @Test
    fun `decode handles payload split into multiple zstd frames`() = runTest {
        val string = "zstd".repeat(DEFAULT_BUFFER_SIZE)
        val encodedReadChannel = ZstdEncoder().encode(ByteReadChannel(string.toByteArray()))
        val decodedReadChannel = ZstdEncoder().decode(encodedReadChannel)
        val decodedString = decodedReadChannel.readRemaining().readText()

        assertEquals(string, decodedString)
    }

    @Test
    fun `decode handles zstd frames that span multiple buffer reads`() = runTest {
        val string = "zstd".repeat(DEFAULT_BUFFER_SIZE)

        val encodedReadChannel = Zstd(DEFAULT_COMPRESSION_LEVEL).encode(ByteReadChannel(string.toByteArray()))
        val decodedReadChannel = ByteChannel()
        with(Zstd(DEFAULT_COMPRESSION_LEVEL)) {
            encodedReadChannel.decodeTo(decodedReadChannel, ByteBufferPool(capacity = 10, bufferSize = 32))
        }
        decodedReadChannel.close()
        val decodedString = decodedReadChannel.readRemaining().readText()

        assertEquals(string, decodedString)
    }
}
