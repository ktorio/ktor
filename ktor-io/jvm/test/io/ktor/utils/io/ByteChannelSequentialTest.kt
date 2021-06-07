/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io

import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.test.*

class ByteChannelSequentialTest {

    @Test
    fun testReadAvailable() = runBlocking {
        val channel = ByteChannelSequentialJVM(IoBuffer.Empty, true)
        channel.writeFully(byteArrayOf(1, 2))

        val read1 = channel.readAvailable(4) { it.position(it.position() + 4) }
        assertEquals(-1, read1)

        channel.writeFully(byteArrayOf(3, 4))
        val read2 = channel.readAvailable(4) { it.position(it.position() + 4) }
        assertEquals(4, read2)
    }

    @Test
    fun testAwaitContent() = runBlocking {
        val channel = ByteChannelSequentialJVM(IoBuffer.Empty, true)

        var awaitingContent = false
        launch {
            awaitingContent = true
            channel.awaitContent()
            awaitingContent = false
        }

        yield()
        assertTrue(awaitingContent)
        channel.writeByte(1)
        yield()
        assertFalse(awaitingContent)
    }
}
