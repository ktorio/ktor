/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.encoding.zstd

import io.ktor.util.cio.KtorDefaultPool
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Taken from [KtorDefaultPool]
 */
private const val DEFAULT_BUFFER_SIZE = 4098

class ZstdTest {

    @Test
    fun testEncodeDecodeChunkedContent() = runTest {
        val string = "zstd".repeat(DEFAULT_BUFFER_SIZE)
        val encodedReadChannel = ZstdEncoder().encode(ByteReadChannel(string.toByteArray()))
        val decodedReadChannel = ZstdEncoder().decode(encodedReadChannel)
        val decodedString = decodedReadChannel.readRemaining().readText()

        assertEquals(string, decodedString)
    }
}
