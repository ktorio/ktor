/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import kotlinx.coroutines.*
import java.io.*
import kotlin.test.*

class ByteBufferChannelTest {
    @Test
    fun testCompleteExceptionallyJob() {
        val channel = ByteBufferChannel(false)
        Job().also { channel.attachJob(it) }.completeExceptionally(IOException("Text exception"))

        assertFailsWith<IOException> { runBlocking { channel.readByte() } }
    }

    @Test
    fun readRemainingThrowsOnClosed() = runBlocking {
        val channel = ByteBufferChannel(false)
        channel.writeFully(byteArrayOf(1, 2, 3, 4, 5))
        channel.close(IllegalStateException("closed"))

        assertFailsWith<IllegalStateException>("closed") {
            channel.readRemaining()
        }
        Unit
    }
}
