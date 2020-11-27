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

    @Test
    fun testWriteWriteAvailableRaceCondition() = runBlocking {
        testWriteXRaceCondition { it.writeAvailable(1) { it.put(1) } }
    }

    @Test
    fun testWriteByteRaceCondition() = runBlocking {
        testWriteXRaceCondition { it.writeByte(1) }
    }

    @Test
    fun testWriteIntRaceCondition() = runBlocking {
        testWriteXRaceCondition { it.writeInt(1) }
    }

    @Test
    fun testWriteShortRaceCondition() = runBlocking {
        testWriteXRaceCondition { it.writeShort(1) }
    }

    @Test
    fun testWriteLongRaceCondition() = runBlocking {
        testWriteXRaceCondition { it.writeLong(1) }
    }

    private fun testWriteXRaceCondition(writer: suspend (ByteChannel) -> Unit): Unit = runBlocking {
        val channel = ByteBufferChannel(false)

        val job1 = GlobalScope.async {
            try {
                repeat(10_000_000) {
                    writer(channel)
                    channel.flush()
                }
                channel.close()
            } catch (cause: Throwable) {
                channel.close(cause)
                throw cause
            }
        }
        val job2 = GlobalScope.async {
            channel.readRemaining()
        }
        job1.await()
        job2.await()
    }
}
