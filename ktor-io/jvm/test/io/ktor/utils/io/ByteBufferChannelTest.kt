/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io

import kotlinx.coroutines.*
import kotlinx.coroutines.debug.junit4.*
import org.junit.*
import java.io.*
import kotlin.test.*
import kotlin.test.Test

class ByteBufferChannelTest {

    @get:Rule
    public val timeoutRule: CoroutinesTimeout by lazy { CoroutinesTimeout.seconds(60) }

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

    @Test
    fun testReadAvailable() = runBlocking {
        val channel = ByteBufferChannel(true)
        channel.writeFully(byteArrayOf(1, 2))

        val read1 = channel.readAvailable(4) { it.position(it.position() + 4) }
        assertEquals(-1, read1)

        channel.writeFully(byteArrayOf(3, 4))
        val read2 = channel.readAvailable(4) { it.position(it.position() + 4) }
        assertEquals(4, read2)
    }

    @Test
    fun testAwaitContent() = runBlocking {
        val channel = ByteBufferChannel(true)

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
